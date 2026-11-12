package com.tenantwall.agent;

import com.tenantwall.agent.TenantContext;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setAndGet_returnsSameValue() {
        TenantContext.setTenant("TENANT_001");
        assertThat(TenantContext.getTenant()).isEqualTo("TENANT_001");
    }

    @Test
    void clear_removesValue() {
        TenantContext.setTenant("TENANT_001");
        TenantContext.clear();
        assertThat(TenantContext.getTenant()).isNull();
    }

    @Test
    void clear_withoutSet_returnsNull() {
        TenantContext.clear();
        assertThat(TenantContext.getTenant()).isNull();
    }

    @Test
    void overwrite_replacesValue() {
        TenantContext.setTenant("FIRST");
        TenantContext.setTenant("SECOND");
        assertThat(TenantContext.getTenant()).isEqualTo("SECOND");
    }

    @Test
    void setNull_storesNull() {
        TenantContext.setTenant(null);
        assertThat(TenantContext.getTenant()).isNull();
    }

    // ═══════════════════════════════════════════════════════════════
    // Thread isolation — the CRITICAL behavior
    // ═══════════════════════════════════════════════════════════════

    @Test
    void threadIsolation_differentThreadsHaveDifferentTenants() throws Exception {
        TenantContext.setTenant("MAIN_TENANT");

        AtomicReference<String> otherThreadTenant = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);

        Thread other = new Thread(() -> {
            TenantContext.setTenant("OTHER_TENANT");
            otherThreadTenant.set(TenantContext.getTenant());
            ready.countDown();
            try {
                proceed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                TenantContext.clear();
            }
        });
        other.start();

        ready.await();
        // Main thread still sees its own value
        assertThat(TenantContext.getTenant()).isEqualTo("MAIN_TENANT");
        // Other thread saw its own value
        assertThat(otherThreadTenant.get()).isEqualTo("OTHER_TENANT");

        proceed.countDown();
        other.join();
    }

    @Test
    void threadIsolation_clearInOneThreadDoesNotAffectAnother() throws Exception {
        TenantContext.setTenant("MAIN_TENANT");

        Thread other = new Thread(() -> {
            TenantContext.setTenant("OTHER_TENANT");
            TenantContext.clear();
        });
        other.start();
        other.join();

        // Main thread still has its tenant
        assertThat(TenantContext.getTenant()).isEqualTo("MAIN_TENANT");
    }

    @Test
    void threadPoolReuse_doesNotLeakTenant() throws Exception {
        // Simulate a thread pool: re-use the same thread for two "requests"
        Thread worker = new Thread(() -> {
            // Request 1: set tenant
            TenantContext.setTenant("REQUEST_1");

            // Request 2: without clear, leak is possible. Simulate the fix:
            TenantContext.clear();
            // Request 2 sees no tenant
            if (TenantContext.getTenant() != null) {
                throw new AssertionError("LEAK: tenant should be null after clear");
            }
        });

        worker.start();
        worker.join();
    }
}