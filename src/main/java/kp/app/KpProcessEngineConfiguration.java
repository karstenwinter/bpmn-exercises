package kp.app;

import javax.sql.DataSource;

import org.camunda.bpm.engine.spring.ProcessEngineFactoryBean;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Engine configuration.
 * 
 * @author broxp
 */
@Configuration
@ComponentScan("kp.app")
class KpProcessEngineConfiguration {
	@Bean
	public DataSource dataSource() {
		// Use a JNDI data source or read the properties from
		// env or a properties file.
		// Note: The following shows only a simple data source
		// for In-Memory H2 database.
		SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
		dataSource.setDriverClass(org.h2.Driver.class);
		dataSource.setUrl("jdbc:h2:mem:camunda;DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");
		return dataSource;
	}

	@Bean
	public PlatformTransactionManager transactionManager() {
		return new DataSourceTransactionManager(dataSource());
	}

	@Bean
	public SpringProcessEngineConfiguration processEngineConfiguration() {
		SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();
		config.setDataSource(dataSource());
		config.setTransactionManager(transactionManager());
		config.setDatabaseSchemaUpdate("true");
		config.setHistory("audit");
		config.setJobExecutorActivate(true);
		// config.setDeploymentResources(new Resource[] { new
		// ClassPathResource("process.xml") });
		return config;
	}

	@Bean
	public ProcessEngineFactoryBean processEngine() {
		ProcessEngineFactoryBean factoryBean = new ProcessEngineFactoryBean();
		factoryBean.setProcessEngineConfiguration(processEngineConfiguration());
		return factoryBean;
	}

	// more engine services and additional beans ...
}