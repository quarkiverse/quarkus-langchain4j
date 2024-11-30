package org.acme.example.multiple;

import java.lang.reflect.Field;

import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.Subclass;

public class SubclassUtil {

    public static <T> T unwrap(T target) {
        T sub = ClientProxy.unwrap(target);
        if (sub instanceof Subclass) {
            try {
                Field delegate = sub.getClass().getDeclaredField("delegate");
                delegate.setAccessible(true);
                sub = (T) delegate.get(sub);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return sub;
    }

}
