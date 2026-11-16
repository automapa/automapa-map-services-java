package pl.automapa.mapservices.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemorySessionStorageTest {

    @Test
    void isEmptyInitially() {
        assertThat(new InMemorySessionStorage().get()).isNull();
    }

    @Test
    void storesAndReturnsSessionId() {
        InMemorySessionStorage storage = new InMemorySessionStorage();

        storage.store("qPPdEpdtpb");

        assertThat(storage.get()).isEqualTo("qPPdEpdtpb");
    }

    @Test
    void storeReplacesPreviousSessionId() {
        InMemorySessionStorage storage = new InMemorySessionStorage();
        storage.store("first");

        storage.store("second");

        assertThat(storage.get()).isEqualTo("second");
    }

    @Test
    void clearRemovesSessionId() {
        InMemorySessionStorage storage = new InMemorySessionStorage();
        storage.store("qPPdEpdtpb");

        storage.clear();

        assertThat(storage.get()).isNull();
    }

    @Test
    void rejectsEmptySessionId() {
        InMemorySessionStorage storage = new InMemorySessionStorage();

        assertThatThrownBy(() -> storage.store(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clear()");
        assertThatThrownBy(() -> storage.store(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clear()");
    }

    @Test
    void valueStoredInOneThreadIsVisibleInAnother() throws Exception {
        InMemorySessionStorage storage = new InMemorySessionStorage();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> writers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String id = "id-" + i;
                writers.add(executor.submit(() -> {
                    start.await();
                    storage.store(id);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> writer : writers) {
                writer.get(5, TimeUnit.SECONDS);
            }

            String seen = executor.submit(storage::get).get(5, TimeUnit.SECONDS);

            assertThat(seen).matches("id-[0-7]");
        } finally {
            executor.shutdownNow();
        }
    }
}
