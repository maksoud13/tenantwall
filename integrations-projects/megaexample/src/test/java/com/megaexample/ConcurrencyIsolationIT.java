package com.megaexample;

import com.megaexample.domain.Product;
import com.megaexample.repo.ProductRepository;
import com.tenantwall.agent.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConcurrencyIsolationIT {

    private static final String TENANT_A = "TENANT_001";
    private static final String TENANT_B = "TENANT_002";

    @Autowired
    private ProductRepository productRepo;

    @BeforeEach
    void setup() {
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("1000 parallel queries — 0 cross-tenant leaks")
    void test1000Parallel() throws Exception {
        int totalRequests = 1000;
        int parallelThreads = 20;   // < Hikari max-pool-size (50)
        int pageSize = 100;         // 100 rows per query (not all 40K)

        AtomicInteger leaks = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger rowsChecked = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(parallelThreads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalRequests);

        for (int i = 0; i < totalRequests; i++) {
            final String tenant = (i % 2 == 0) ? TENANT_A : TENANT_B;
            final String other = (i % 2 == 0) ? TENANT_B : TENANT_A;

            pool.submit(() -> {
                try {
                    start.await();  // fire together
                    TenantContext.setTenant(tenant);

                    // Real JPA query → Hibernate → agent → MySQL
                    List<Product> page = productRepo
                            .findByActive(true, PageRequest.of(0, pageSize))
                            .getContent();

                    for (Product p : page) {
                        String rowTenant = p.getTenantId();
                        rowsChecked.incrementAndGet();

                        if (other.equals(rowTenant)) {
                            leaks.incrementAndGet();
                            System.err.printf("LEAK: %s saw %s (id=%d)%n",
                                    tenant, rowTenant, p.getId());
                        } else if (!tenant.equals(rowTenant)) {
                            errors.incrementAndGet();
                        }
                    }
                    completed.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                    System.err.println("ERROR: " + e);
                    e.printStackTrace();
                } finally {
                    TenantContext.clear();
                    done.countDown();
                }
            });
        }

        long t0 = System.currentTimeMillis();
        start.countDown();
        boolean finished = done.await(180, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - t0;
        pool.shutdown();

        // ═══ Summary ═══
        System.out.printf("%n═══════════ Concurrency Test Result ═══════════%n");
        System.out.printf("  Requests:    %d%n", totalRequests);
        System.out.printf("  Threads:     %d%n", parallelThreads);
        System.out.printf("  Page size:   %d%n", pageSize);
        System.out.printf("  Completed:   %d%n", completed.get());
        System.out.printf("  Rows read:   %,d%n", rowsChecked.get());
        System.out.printf("  Leaks:       %d%n", leaks.get());
        System.out.printf("  Errors:      %d%n", errors.get());
        System.out.printf("  Duration:    %.1fs%n", elapsed / 1000.0);
        System.out.printf("════════════════════════════════════════════════%n%n");

        // ═══ Assertions ═══
        assertThat(finished)
                .as("All %d requests completed within 180s", totalRequests)
                .isTrue();
        assertThat(completed.get())
                .as("Successful requests")
                .isEqualTo(totalRequests);
        assertThat(errors.get())
                .as("Errors")
                .isZero();
        assertThat(leaks.get())
                .as("Cross-tenant leaks — MUST be 0")
                .isZero();
        assertThat(rowsChecked.get())
                .as("Total rows verified")
                .isEqualTo(totalRequests * pageSize);
    }

    @Test
    @DisplayName("ThreadLocal cleared after parallel run")
    void threadLocalIsClean() {
        assertThat(TenantContext.getTenant())
                .as("TenantContext must be null after test")
                .isNull();
    }
}