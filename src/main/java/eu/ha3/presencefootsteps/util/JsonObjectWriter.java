package eu.ha3.presencefootsteps.util;

import java.io.IOException;

import com.google.gson.stream.JsonWriter;

public interface JsonObjectWriter extends AutoCloseable {

    static JsonObjectWriter of(JsonWriter writer) throws IOException {
        writer.setIndent("    ");
        return () -> writer;
    }

    JsonWriter writer();

    default void field(String name, String data) throws IOException {
        writer().name(name);
        writer().value(data);
    }

    default void field(String name, int data) throws IOException {
        writer().name(name);
        writer().value(data);
    }

    default void field(String name, long data) throws IOException {
        writer().name(name);
        writer().value(data);
    }

    default void field(String name, float data) throws IOException {
        writer().name(name);
        writer().value(data);
    }

    default void field(String name, WriteAction action) throws IOException {
        writer().name(name);
        action.write();
    }

    default void object(WriteAction action) throws IOException {
        writer().beginObject();
        action.write();
        writer().endObject();
    }

    default void object(String name, WriteAction action) throws IOException {
        writer().name(name);
        object(action);
    }

    default void array(String name, WriteAction action) throws IOException {
        writer().name(name);
        array(action);
    }

    default void array(WriteAction action) throws IOException {
        writer().beginArray();
        action.write();
        writer().endArray();
    }

    default <T> void each(Iterable<T> iterable, WriteConsumer<T> action) throws IOException {
        for (T t : iterable) {
            action.write(t);
        }
    }

    @Override
    default void close() throws IOException {
        writer().close();
    }

    interface WriteAction {
        void write() throws IOException;
    }

    interface WriteConsumer<T> {
        void write(T t) throws IOException;
    }
}
