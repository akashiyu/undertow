package io.undertow.testutils;

import org.xnio.Pool;
import org.xnio.Pooled;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Stuart Douglas
 */
public class DebuggingSlicePool implements Pool<ByteBuffer>{

    /**
     * context that can be added to allocations to give more information about buffer leaks, useful when debugging buffer leaks
     */
    private static final ThreadLocal<String> ALLOCATION_CONTEXT = new ThreadLocal<>();

    static final Set<DebuggingBuffer> BUFFERS = Collections.newSetFromMap(new ConcurrentHashMap<DebuggingBuffer, Boolean>());
    static volatile String currentLabel;

    private final Pool<ByteBuffer> delegate;

    public DebuggingSlicePool(Pool<ByteBuffer> delegate) {
        this.delegate = delegate;
    }

    public static void addContext(String context) {
        ALLOCATION_CONTEXT.set(context);
    }

    @Override
    public Pooled<ByteBuffer> allocate() {
        final Pooled<ByteBuffer> delegate = this.delegate.allocate();
        return new DebuggingBuffer(delegate, currentLabel);
    }

    static class DebuggingBuffer implements Pooled<ByteBuffer> {

        private static final AtomicInteger allocationCount = new AtomicInteger();
        private final RuntimeException allocationPoint;
        private final Pooled<ByteBuffer> delegate;
        private final String label;
        private final int no;
        private volatile boolean free = false;
        private RuntimeException freePoint;

        public DebuggingBuffer(Pooled<ByteBuffer> delegate, String label) {
            this.delegate = delegate;
            this.label = label;
            this.no = allocationCount.getAndIncrement();
            String ctx = ALLOCATION_CONTEXT.get();
            ALLOCATION_CONTEXT.remove();
            allocationPoint = new RuntimeException(delegate.getResource()  + " NO: " + no + " " + (ctx == null ? "[NO_CONTEXT]" : ctx));
            BUFFERS.add(this);
        }

        @Override
        public void discard() {
            BUFFERS.remove(this);
            delegate.discard();
        }

        @Override
        public void free() {
            if(free) {
                return;
            }
            freePoint = new RuntimeException("FREE POINT");
            free = true;
            BUFFERS.remove(this);
            delegate.free();
        }

        @Override
        public ByteBuffer getResource() throws IllegalStateException {
            if(free) {
                throw new IllegalStateException("Buffer already freed, free point: ", freePoint);
            }
            return delegate.getResource();
        }

        @Override
        public void close() {
        }

        RuntimeException getAllocationPoint() {
            return allocationPoint;
        }

        String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return "[debug]" + delegate.toString();
        }
    }
}
