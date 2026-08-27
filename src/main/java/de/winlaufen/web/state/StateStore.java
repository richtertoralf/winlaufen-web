package de.winlaufen.web.state;

import de.winlaufen.web.model.*;
import de.winlaufen.web.protocol.ResultBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class StateStore {
    private final AtomicReference<AppState> state = new AtomicReference<>(AppState.empty());
    private final List<Consumer<StateEvent>> listeners = new CopyOnWriteArrayList<>();

    public AppState get() { return state.get(); }
    public void addListener(Consumer<StateEvent> listener) { listeners.add(listener); }

    public synchronized void health(ConnectionHealth health) {
        AppState old = state.get();
        if (old.health() == health) return;
        publish(new AppState(old.revision() + 1, health, old.clock(), old.competition(), old.currentFinish(), old.message()),
                StateEvent.Type.SNAPSHOT, -1);
    }

    public synchronized void clock(String clock) {
        AppState old = state.get();
        publish(new AppState(old.revision() + 1, ConnectionHealth.CONNECTED, clock,
                old.competition(), old.currentFinish(), old.message()), StateEvent.Type.CLOCK, -1);
    }

    public synchronized void result(ResultBlock block) {
        AppState old = state.get();
        long revision = old.revision() + 1;
        boolean sameCompetition = old.competition() != null
                && old.competition().type().equals(block.competitionType())
                && old.competition().evaluationMode() == block.evaluationMode()
                && old.competition().classes().size() == block.classNames().length
                && Arrays.equals(old.competition().classes().stream().map(CompetitionClass::name).toArray(String[]::new), block.classNames());
        List<CompetitionClass> classes = new ArrayList<>(block.classNames().length);
        for (int i = 0; i < block.classNames().length; i++) {
            ClassSnapshot snapshot = null;
            if (sameCompetition) {
                snapshot = old.competition().classes().get(i).snapshot();
            }
            if (i == block.classIndex()) {
                snapshot = new ClassSnapshot(revision, block.headers(), block.rows());
            }
            classes.add(new CompetitionClass(i, block.classNames()[i], block.roundsOrTeamSize()[i], snapshot));
        }
        Competition competition = new Competition(block.competitionType(), block.evaluationMode(),
                block.classNames().length, block.winSpringenPosition(), block.roundOrHeat(), classes);
        CurrentFinish finish = new CurrentFinish(block.classIndex(), block.currentFinishIndex(), revision);
        publish(new AppState(revision, old.health(), old.clock(), competition, finish, old.message()),
                StateEvent.Type.CLASS_SNAPSHOT, block.classIndex());
    }

    public synchronized void message(String message) {
        AppState old = state.get();
        publish(new AppState(old.revision() + 1, old.health(), old.clock(), old.competition(),
                old.currentFinish(), message), StateEvent.Type.MESSAGE, -1);
    }

    private void publish(AppState next, StateEvent.Type type, int classIndex) {
        state.set(next);
        StateEvent event = new StateEvent(type, next, classIndex);
        listeners.forEach(listener -> listener.accept(event));
    }
}
