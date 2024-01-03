package hudson.model;

import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.FutureImpl;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock version of {@link Queue.WaitingItem} (which is final) that avoids {@link jenkins.model.TransientActionFactory}.
 */
public class MockWaitingItem extends Queue.Item {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    /**
     * Create a new mock waiting item.
     *
     * @param project The project.
     * @param actions The actions.
     */
    public MockWaitingItem(Queue.Task project, List<Action> actions) {
        super(project, actions, COUNTER.incrementAndGet(), new FutureImpl(project));
    }

    @SuppressWarnings("deprecation") // avoid TransientActionFactory
    @Override
    public <T extends Action> T getAction(Class<T> type) {
        for (Action a : getActions()) {
            if (type.isInstance(a)) {
                return type.cast(a);
            }
        }
        return null;
    }

    @Override
    public boolean isBuildable() {
        return false;
    }

    @Override
    public CauseOfBlockage getCauseOfBlockage() {
        throw new UnsupportedOperationException();
    }

    @Override
    void enter(Queue q) {
        throw new UnsupportedOperationException();
    }

    @Override
    boolean leave(Queue q) {
        throw new UnsupportedOperationException();
    }
}
