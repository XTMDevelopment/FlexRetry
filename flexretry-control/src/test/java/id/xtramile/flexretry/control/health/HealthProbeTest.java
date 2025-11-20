package id.xtramile.flexretry.control.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthProbeTest {

    @Test
    void testState_EnumValues() {
        assertNotNull(HealthProbe.State.UP);
        assertNotNull(HealthProbe.State.DEGRADED);
        assertNotNull(HealthProbe.State.DOWN);

        assertEquals(3, HealthProbe.State.values().length);
    }

    @Test
    void testHealthProbe_Implementation() {
        HealthProbe probe = () -> HealthProbe.State.UP;
        
        assertEquals(HealthProbe.State.UP, probe.state());
    }

    @Test
    void testState_AllStates() {
        HealthProbe probeUp = () -> HealthProbe.State.UP;
        HealthProbe probeDegraded = () -> HealthProbe.State.DEGRADED;
        HealthProbe probeDown = () -> HealthProbe.State.DOWN;
        
        assertEquals(HealthProbe.State.UP, probeUp.state());
        assertEquals(HealthProbe.State.DEGRADED, probeDegraded.state());
        assertEquals(HealthProbe.State.DOWN, probeDown.state());
    }
}

