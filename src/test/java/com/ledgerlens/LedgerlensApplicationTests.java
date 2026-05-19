package com.ledgerlens;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "minio.initialize-bucket=false")
class LedgerlensApplicationTests {

	@Test
	void contextLoads() {
	}

}
