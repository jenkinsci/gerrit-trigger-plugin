package hudson.model;

import hudson.model.queue.CauseOfBlockage;

/**
 * Mock version of {@link Queue.BuildableItem} (which is final) that avoids {@link jenkins.model.TransientActionFactory}.
 */
public class MockBuildableItem extends Queue.Item {

    /**
     * Create a new mock buildable item.
     *
     * @param item The item.
     */
    public MockBuildableItem(Queue.Item item) {
        super(item);
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
        return true;
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
