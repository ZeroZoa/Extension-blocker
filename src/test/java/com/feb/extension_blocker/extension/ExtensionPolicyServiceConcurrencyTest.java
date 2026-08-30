package com.feb.extension_blocker.extension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 실제 PostgreSQL에 붙여서, 199개 상태에서 동시에 여러 요청이 들어와도 200개를 넘지
 * 않는지 검증한다. Mockito로는 여러 스레드가 각자 다른 DB 커넥션/트랜잭션으로 동시에
 * count를 확인하고 insert하는 흐름을 재현할 수 없어, 이 시나리오만큼은 실제 DB가 필요하다.
 */
@SpringBootTest
class ExtensionPolicyServiceConcurrencyTest {

    private static final int SEED_COUNT = 199;
    private static final int CONCURRENT_REQUESTS = 5;

    @Autowired
    private ExtensionPolicyService extensionPolicyService;

    @Autowired
    private ExtensionPolicyRepository extensionPolicyRepository;

    @BeforeEach
    void seedToMaxMinusOne() {
        for (int i = 0; i < SEED_COUNT; i++) {
            extensionPolicyRepository.save(new ExtensionPolicy("seed" + i, ExtensionType.CUSTOM, true));
        }
    }

    @AfterEach
    void cleanUp() {
        extensionPolicyRepository.findByTypeOrderByIdAsc(ExtensionType.CUSTOM)
                .forEach(extensionPolicyRepository::delete);
    }

    @Test
    @DisplayName("199개 상태에서 동시에 5개 요청이 들어와도 200개를 넘지 않는다")
    void doesNotExceedMaxCountUnderConcurrentInserts() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            String extension = "race" + i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    extensionPolicyService.addCustomExtension(extension);
                    succeeded.incrementAndGet();
                } catch (ExtensionValidationException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(1, succeeded.get(), "199개 상태에서 여유는 1개뿐이라 정확히 1개만 성공해야 한다");
        assertEquals(CONCURRENT_REQUESTS - 1, rejected.get());
        assertEquals(200, extensionPolicyRepository.countByType(ExtensionType.CUSTOM));
    }
}
