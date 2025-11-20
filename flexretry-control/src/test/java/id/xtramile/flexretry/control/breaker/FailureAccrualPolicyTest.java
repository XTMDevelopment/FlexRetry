package id.xtramile.flexretry.control.breaker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailureAccrualPolicyTest {

    @Test
    void testSimpleFailureAccrualPolicy() {
        FailureAccrualPolicy policy = new FailureAccrualPolicy() {
            private int failures = 0;
            private int successes = 0;
            private static final int THRESHOLD = 5;

            @Override
            public boolean recordSuccess() {
                successes++;
                return true;
            }

            @Override
            public boolean recordFailure() {
                failures++;
                return failures >= THRESHOLD;
            }

            @Override
            public boolean isTripped() {
                return failures >= THRESHOLD;
            }

            @Override
            public void reset() {
                failures = 0;
                successes = 0;
            }
        };

        assertFalse(policy.isTripped());

        for (int i = 0; i < 4; i++) {
            assertFalse(policy.recordFailure());
            assertFalse(policy.isTripped());
        }

        assertTrue(policy.recordFailure());
        assertTrue(policy.isTripped());

        policy.recordSuccess();

        policy.reset();
        assertFalse(policy.isTripped());

        assertFalse(policy.recordFailure());
        assertFalse(policy.isTripped());
    }
}

