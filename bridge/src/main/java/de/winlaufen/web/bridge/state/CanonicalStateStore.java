package de.winlaufen.web.bridge.state;

import de.winlaufen.web.bridge.source.winlaufen.ResultBlock;
import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.ClockSample;
import de.winlaufen.web.contract.CompetitionTimeOffset;
import de.winlaufen.web.contract.CompetitionTimeZoneSource;
import de.winlaufen.web.contract.TimeReference;
import de.winlaufen.web.contract.ClassSnapshot;
import de.winlaufen.web.contract.Competition;
import de.winlaufen.web.contract.CompetitionClass;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.CurrentFinish;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The single canonical, output-neutral competition state of the bridge.
 *
 * <p>Every accepted update produces a new immutable snapshot with a monotonically increasing
 * {@code sourceRevision}. Values are stored exactly as the source supplied them; only structural
 * contract limits are enforced, so that an adopted state is always publishable.
 */
public final class CanonicalStateStore {

    private final AtomicReference<CanonicalSnapshot> current;
    private final List<Consumer<CanonicalSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final TimeReference reference;
    private final String competitionZoneId;
    private final CompetitionTimeZoneSource competitionZoneSource;
    private long clockSampleRevision;

    /**
     * @param configuredZoneId the organiser's competition time zone, or {@code null} when none was
     *                         configured — then the zone of this machine is used and every sample
     *                         says that it is only a fallback
     */
    public CanonicalStateStore(PresentationConfig presentation, String configuredZoneId) {
        this(presentation, TimeReference.systemClock(), configuredZoneId);
    }

    /** Test seam: makes the measurement deterministic without touching the machine's clock. */
    public CanonicalStateStore(PresentationConfig presentation, TimeReference reference,
                               String configuredZoneId) {
        this.reference = reference;
        this.competitionZoneId = configuredZoneId != null
                ? configuredZoneId : ZoneId.systemDefault().getId();
        this.competitionZoneSource = configuredZoneId != null
                ? CompetitionTimeZoneSource.CONFIGURED : CompetitionTimeZoneSource.SYSTEM_DEFAULT;
        current = new AtomicReference<>(new CanonicalSnapshot(0, CanonicalState.empty(), presentation));
    }

    public CanonicalSnapshot get() {
        return current.get();
    }

    /** The zone in which the competition time is read, as it is actually being used right now. */
    public String competitionTimeZone() {
        return competitionZoneId;
    }

    /** Whether that zone was configured or only taken from this machine. */
    public CompetitionTimeZoneSource competitionTimeZoneSource() {
        return competitionZoneSource;
    }

    /** @return a handle that removes the listener again; used by the output target manager. */
    public AutoCloseable addListener(Consumer<CanonicalSnapshot> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public synchronized void presentation(PresentationConfig value) {
        CanonicalSnapshot old = current.get();
        publish(old.sourceRevision() + 1, old.state(), value);
    }

    public synchronized void health(SourceHealth value) {
        CanonicalSnapshot old = current.get();
        if (old.state().sourceHealth() == value) {
            return;
        }
        publish(old.sourceRevision() + 1,
                new CanonicalState(value, old.state().clock(), old.state().competition(),
                        old.state().currentFinish(), old.state().message(), old.state().clockSample()),
                old.presentation());
    }

    /**
     * A recognised WinLaufen clock telegram, and the only place a clock sample is created.
     *
     * <p>The instant is read before the lock is taken, so the measurement belongs to the moment the
     * telegram arrived and not to whenever this store happened to become free. Every telegram
     * produces a sample, including one that repeats the current value: that repetition is a real
     * observation of the source even though the competition time did not move.
     */
    public void clock(String value) {
        Instant at = reference.now();
        applyClock(value, at);
    }

    private synchronized void applyClock(String value, Instant at) {
        CanonicalSnapshot old = current.get();
        ClockSample sample = new ClockSample(++clockSampleRevision, value, competitionZoneId,
                competitionZoneSource, at.toString(),
                CompetitionTimeOffset.differenceMillis(value, competitionZoneId, at),
                reference.status(), reference.source());
        publish(old.sourceRevision() + 1,
                new CanonicalState(SourceHealth.CONNECTED, value, old.state().competition(),
                        old.state().currentFinish(), old.state().message(), sample),
                old.presentation());
    }

    public synchronized void message(String value) {
        CanonicalSnapshot old = current.get();
        publish(old.sourceRevision() + 1,
                new CanonicalState(old.state().sourceHealth(), old.state().clock(),
                        old.state().competition(), old.state().currentFinish(), value,
                        old.state().clockSample()),
                old.presentation());
    }

    /**
     * Adopts one complete class snapshot. Snapshots of the other classes are retained only while
     * the competition structure is unchanged, exactly as the pre-modular store behaved.
     */
    public synchronized void result(ResultBlock block) {
        CanonicalSnapshot old = current.get();
        long revision = old.sourceRevision() + 1;
        Competition prior = old.state().competition();
        boolean sameCompetition = prior != null
                && prior.type().equals(block.competitionType())
                && prior.evaluationMode() == block.evaluationMode()
                && prior.classes().size() == block.classNames().length
                && Arrays.equals(prior.classes().stream().map(CompetitionClass::name).toArray(String[]::new),
                        block.classNames());

        List<CompetitionClass> classes = new ArrayList<>(block.classNames().length);
        for (int index = 0; index < block.classNames().length; index++) {
            ClassSnapshot snapshot = sameCompetition ? prior.classes().get(index).snapshot() : null;
            if (index == block.classIndex()) {
                snapshot = new ClassSnapshot(revision, block.headers(), block.rows());
            }
            classes.add(new CompetitionClass(index, block.classNames()[index],
                    block.roundsOrTeamSize()[index], snapshot));
        }
        Competition competition = new Competition(block.competitionType(), block.evaluationMode(),
                classes.size(), block.winSpringenPosition(), block.roundOrHeat(), classes);

        publish(revision,
                new CanonicalState(old.state().sourceHealth(), old.state().clock(), competition,
                        new CurrentFinish(block.classIndex(), block.currentFinishIndex(), revision),
                        old.state().message(), old.state().clockSample()),
                old.presentation());
    }

    private void publish(long revision, CanonicalState state, PresentationConfig presentation) {
        // Never adopt a state that could not be published over the contract afterwards.
        ContractJson.validateState(state, presentation);
        publish(new CanonicalSnapshot(revision, state, presentation));
    }

    private void publish(CanonicalSnapshot next) {
        current.set(next);
        listeners.forEach(listener -> listener.accept(next));
    }
}
