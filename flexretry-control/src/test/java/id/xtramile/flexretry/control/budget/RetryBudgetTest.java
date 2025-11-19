package id.xtramile.flexretry.control.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryBudgetTest {

    @Test
    void testUnlimited() {
        RetryBudget budget = RetryBudget.unlimited();

        for (int i = 0; i < 100; i++) {
            assertTrue(budget.tryAcquire());
        }
    }
}

