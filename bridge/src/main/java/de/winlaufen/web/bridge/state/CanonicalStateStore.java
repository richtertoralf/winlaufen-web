package de.winlaufen.web.bridge.state;

import de.winlaufen.web.bridge.source.winlaufen.ResultBlock;
import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.ClassSnapshot;
import de.winlaufen.web.contract.Competition;
import de.winlaufen.web.contract.CompetitionClass;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.CurrentFinish;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;

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

    public CanonicalStateStore(PresentationConfig presentation) {
        current = new AtomicReference<>(new CanonicalSnapshot(0, CanonicalState.empty(), presentation));
    }

    public CanonicalSnapshot get() {
        return current.get();
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
                        old.state().currentFinish(), old.state().message()),
                old.presentation());
    }

    public synchronized void clock(String value) {
        CanonicalSnapshot old = current.get();
        publish(old.sourceRevision() + 1,
                new CanonicalState(SourceHealth.CONNECTED, value, old.state().competition(),
                        old.state().currentFinish(), old.state().message()),
                old.presentation());
    }

    public synchronized void message(String value) {
        CanonicalSnapshot old = current.get();
        publish(old.sourceRevision() + 1,
                new CanonicalState(old.state().sourceHealth(), old.state().clock(),
                        old.state().competition(), old.state().currentFinish(), value),
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
                        old.state().message()),
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
