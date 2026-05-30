package com.ledgerlens.system;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemStatusControllerTest {

    @Test
    void status_returnsRuntimeMetadata() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        SystemStatusController controller = new SystemStatusController(environment);

        SystemStatusResponse response = controller.status();

        assertThat(response.application()).isEqualTo("ledgerlens");
        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.uptimeSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(response.javaVersion()).isNotBlank();
        assertThat(response.activeProfiles()).containsExactly("test");
    }
}
