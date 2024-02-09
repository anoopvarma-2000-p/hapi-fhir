package ca.uhn.fhir.jpa.config.util;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ConnectionPoolInfoProviderTest {

	public static final long MAX_WAIT_MILLIS = 10_000;
	public static final int MAX_CONNECTIONS_TOTAL = 50;

	private IConnectionPoolInfoProvider tested;


	@Nested
	public class TestBasiDataSourceImplementation {

		@BeforeEach
		void setUp() {
			BasicDataSource myDataSource = new BasicDataSource();
			myDataSource.setMaxWaitMillis(MAX_WAIT_MILLIS);
			myDataSource.setMaxTotal(MAX_CONNECTIONS_TOTAL);
			tested = new BasicDataSourceConnectionPoolInfoProvider(myDataSource);
		}


		@Test
		void testGetMaxWaitMillis() {
			Optional<Long> resOpt = tested.getMaxWaitMillis();
			assertThat(resOpt).isPresent();
			assertThat(resOpt).contains(MAX_WAIT_MILLIS);
		}

		@Test
		void testGetMaxConnectionSize() {
			Optional<Integer> resOpt = tested.getTotalConnectionSize();
			assertThat(resOpt).isPresent();
			assertThat(resOpt).contains(MAX_CONNECTIONS_TOTAL);
		}

	}


	@Nested
	public class TestFailedProviderSetup {

		@Mock DataSource unknownDataSource;

		@BeforeEach
		void setUp() {
			tested = new ConnectionPoolInfoProvider(unknownDataSource);
		}


		@Test
		void testGetMaxWaitMillis() {
			Optional<Long> resOpt = tested.getMaxWaitMillis();
			assertThat(resOpt.isPresent()).isFalse();
		}

		@Test
		void testGetMaxConnectionSize() {
			Optional<Integer> resOpt = tested.getTotalConnectionSize();
			assertThat(resOpt.isPresent()).isFalse();
		}

		@Test
		void testGetActiveConnections() {
			Optional<Integer> resOpt = tested.getActiveConnections();
			assertThat(resOpt.isPresent()).isFalse();
		}

	}

	@Nested
	public class TestConfig {

		@Mock DataSource unknownDataSource;

		@Test
		void dataSourceIsBasicDataSource() {
			DataSource ds = new BasicDataSource();

			IConnectionPoolInfoProvider provider = new ConnectionPoolInfoProvider(ds);

			IConnectionPoolInfoProvider instantiatedProvider =
				(IConnectionPoolInfoProvider) ReflectionTestUtils.getField(provider, "myProvider");

			assertThat(instantiatedProvider).isNotNull();
			assertThat(instantiatedProvider.getClass().isAssignableFrom(BasicDataSourceConnectionPoolInfoProvider.class)).isTrue();
		}

		@Test
		void dataSourceIsProxyDataSourceWrappingBasicDataSource() {
			DataSource ds = new BasicDataSource();
			ProxyDataSource proxyDs = new ProxyDataSource(ds);

			IConnectionPoolInfoProvider provider = new ConnectionPoolInfoProvider(proxyDs);

			IConnectionPoolInfoProvider instantiatedProvider =
				(IConnectionPoolInfoProvider) ReflectionTestUtils.getField(provider, "myProvider");
			assertThat(instantiatedProvider).isNotNull();
			assertThat(instantiatedProvider.getClass().isAssignableFrom(BasicDataSourceConnectionPoolInfoProvider.class)).isTrue();
		}

		@Test
		void dataSourceIsProxyDataSourceWrappingNotBasicDataSource() {
			ProxyDataSource proxyDs = new ProxyDataSource(unknownDataSource);

			IConnectionPoolInfoProvider provider = new ConnectionPoolInfoProvider(proxyDs);
			IConnectionPoolInfoProvider instantiatedProvider =
				(IConnectionPoolInfoProvider) ReflectionTestUtils.getField(provider, "myProvider");
			assertThat(instantiatedProvider).isNull();
		}

		@Test
		void dataSourceIsNotBasicDataSourceOrProxyDataSource() {
			IConnectionPoolInfoProvider provider = new ConnectionPoolInfoProvider(unknownDataSource);

			IConnectionPoolInfoProvider instantiatedProvider =
				(IConnectionPoolInfoProvider) ReflectionTestUtils.getField(provider, "myProvider");
			assertThat(instantiatedProvider).isNull();
		}

	}

}
