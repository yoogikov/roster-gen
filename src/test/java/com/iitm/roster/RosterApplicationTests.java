package com.iitm.roster;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class RosterApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() {
		// Passes if the Spring context starts; any wiring error fails the test before this line.
	}

	@Test
	void devProfileUsesInMemoryH2() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:roster");
		}
	}

}
