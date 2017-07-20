package kp.app;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.repository.DeploymentWithDefinitions;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

/**
 * Simple Spring boot app for trying out a Camunda process.
 * 
 * @author broxp
 */
@SpringBootApplication
public class App {
	private static final Log log = LogFactory.getLog(App.class);

	public static final String KP_PROCESS = "kp-process";
	public static final String KP_DECISION = "dmn1";
	public static final String BPMN_FILE = "process.bpmn";
	public static final String DMN_FILE = "decision.dmn";
	private static String deployedProcessId;

	/**
	 * Returns the id of the deployed BPMN process. Needed by some Camunda methods.
	 */
	public static String getProcessId() {
		return deployedProcessId;
	}

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

	@Autowired
	private ProcessEngine engine;

	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			String[] beanNames = ctx.getBeanDefinitionNames();
			Arrays.sort(beanNames);

			log.debug("Beans provided by Spring Boot:");
			log.debug(Arrays.deepToString(beanNames).replaceAll(",", "\n"));
			log.warn("Server started on:\nhttp://localhost:8080\n");

			try {
				RepositoryService repo = engine.getRepositoryService();
				DeploymentBuilder deployment = repo.createDeployment();
				DeploymentWithDefinitions result = deployment
						.addClasspathResource(BPMN_FILE).addClasspathResource(DMN_FILE)
						.deployWithResult();
				List<ProcessDefinition> deplProc = result.getDeployedProcessDefinitions();
				Assert.isTrue(!deplProc.isEmpty(),
						"No process deployed using " + BPMN_FILE);

				List<DecisionDefinition> deplDec = result
						.getDeployedDecisionDefinitions();
				Assert.isTrue(!deplDec.isEmpty(),
						"No decisions deployed using " + DMN_FILE);

				deployedProcessId = deplProc.get(0).getId();

				BpmnModelInstance bpmn = repo.getBpmnModelInstance(deployedProcessId);
				Assert.isTrue(bpmn != null,
						"RepositoryService returned no process model");

				Assert.isTrue(deployedProcessId.startsWith(KP_PROCESS),
						"Process must start with " + KP_PROCESS + ", was "
								+ deployedProcessId);

				log.debug("\nProcesses: " + deplProc);
				log.debug("\nDefinitions: " + deplDec);
			} catch (Exception e) {
				log.error("", e);
				throw e;
			}
		};
	}
}