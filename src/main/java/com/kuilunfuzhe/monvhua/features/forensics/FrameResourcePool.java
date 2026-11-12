package com.kuilunfuzhe.monvhua.features.forensics;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Each deferred draw owns a resource until the frame completes; slots can be reused next frame. */
public final class FrameResourcePool<T> implements AutoCloseable {
    private final ArrayList<T> resources=new ArrayList<>();
    private final Supplier<T> create;
    private final Consumer<T> release;
    private int used;
    public FrameResourcePool(Supplier<T> create,Consumer<T> release){this.create=create;this.release=release;}
    public T acquire(){if(used==resources.size())resources.add(create.get());return resources.get(used++);}
    /** Call only after the previous frame's deferred draws have completed. */
    public void beginFrame(){while(resources.size()>used)release.accept(resources.removeLast());used=0;}
    @Override public void close(){for(T resource:resources)release.accept(resource);resources.clear();used=0;}
}
