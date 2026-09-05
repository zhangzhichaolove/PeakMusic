package com.chao.peakmusic.activity;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.LinkedHashSet;
import java.util.Set;

/** Configuration-safe selection, keyed by media identity. Never put an unbounded list in a Bundle. */
public final class LibrarySelection extends ViewModel {
    public final Set<String> keys = new LinkedHashSet<>();
    public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    public boolean active;
    public boolean busy;
    private Boolean result;

    public void changed() { changes.setValue(changes.getValue() + 1); }
    public void start() { active = true; changed(); }
    public void clear() { keys.clear(); active = false; changed(); }
    public void toggle(String key) {
        if (busy) return;
        if (!keys.remove(key)) keys.add(key);
        changed();
    }
    public void begin() { busy = true; changed(); }
    public void complete(boolean success) { busy = false; result = success; changed(); }
    public Boolean consumeResult() { Boolean value = result; result = null; return value; }
}
