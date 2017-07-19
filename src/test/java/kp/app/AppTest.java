package kp.app;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.camunda.bpm.dmn.engine.DmnDecisionResult;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.dmn.DecisionsEvaluationBuilder;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.repository.DeploymentWithDefinitions;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.bpm.extension.process_test_coverage.junit.rules.TestCoverageProcessEngineRuleBuilder;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests different paths in the process and also some decisions.
 * 
 * <p>
 * Remark: Inheriting from {@link ProcessEngineAssertions} enables easier
 * Organise imports.
 * 
 * @author karsten.pietrzyk
 */
@Deployment(resources = { App.BPMN_FILE, App.DMN_FILE })
public class AppTest extends ProcessEngineAssertions {
	@ClassRule
	public static ProcessEngineRule rule = TestCoverageProcessEngineRuleBuilder.create().build();

	private String id;
	private BpmnModelInstance model;

	private ProcessEngine engine() {
		return rule.getProcessEngine();
	}

	/**
	 * Deploys the process and decision (manually), retrieves id and model for
	 * further use.
	 */
	@Before
	public void setUp() throws Exception {
		ProcessEngineAssertions.init(engine());
		Mocks.register("sendMessage", new SendMessage());

		RepositoryService repositoryService = engine().getRepositoryService();
		assertThat(repositoryService).isNotNull();

		DeploymentBuilder dep = repositoryService.createDeployment();
		DeploymentWithDefinitions result = dep //
				.addClasspathResource(App.BPMN_FILE) //
				.addClasspathResource(App.DMN_FILE) //
				.deployWithResult();
		assertThat(result.getDeployedProcessDefinitions()).isNotEmpty();

		id = result.getDeployedProcessDefinitions().get(0).getId();
		model = repositoryService.getBpmnModelInstance(id);
		assertThat(model).isNotNull();
	}

	@Test
	public void testFullProcess() {
		Map<String, Object> data = new HashMap<>();
		data.put("pizza", "yes");
		data.put("drinks", "yes");
		data.put("review", "yes");

		assertProcessRunsTasks(App.KP_PROCESS, data, "OrderPizzaDrinksTask", "CreateOrderTask", "PreparePizzaTask",
				"PrepareDrinksTask", "OfferMealTask", "PayMealTask", "EatTask", "DrinkTask", "WriteReviewTask",
				"ProcessReviewTask");
	}

	@Test
	public void testShortestProcess() {
		Map<String, Object> data = new HashMap<>();
		data.put("pizza", "null");
		data.put("drinks", "null");
		data.put("review", "null");

		assertProcessRunsTasks(App.KP_PROCESS, data, "OrderPizzaDrinksTask", "CreateOrderTask");
	}

	@Test
	public void testDrinksOnlyWithReviewProcess() {
		Map<String, Object> data = new HashMap<>();
		data.put("pizza", "null");
		data.put("drinks", "yes");
		data.put("review", "yes");

		assertProcessRunsTasks(App.KP_PROCESS, data, "OrderPizzaDrinksTask", "CreateOrderTask", "PrepareDrinksTask",
				"OfferMealTask", "PayMealTask", "DrinkTask", "WriteReviewTask", "ProcessReviewTask");
	}

	/**
	 * JUnit's Parameterized class is over-engineering here.
	 */
	@Test
	public void testDecisions() {
		String[][] triples = { //
				// pizza, drinks, review
				{ "salami", "cola", "Yummy salami pizza and cola." }, //
				{ "salami", "null", "Yummy salami pizza." }, //
				{ "null", "cola", "Great cola." }, //
				{ "other", "other", "Quite ok." }, //
				{ "null", "null", "null" }, //
		};

		for (String[] triple : triples) {
			String pizza = triple[0];
			String drinks = triple[1];
			String result = triple[2];

			DecisionsEvaluationBuilder dmn = engine().getDecisionService().evaluateDecisionByKey(App.KP_DECISION);
			Map<String, Object> data = new HashMap<>();
			data.put("pizza", pizza);
			data.put("drinks", drinks);

			String caseString = Arrays.deepToString(triple);

			DmnDecisionResult res = dmn.variables(data).evaluate();
			assertThat(res).as(caseString).isNotEmpty();
			String val = res.getSingleEntry();
			assertThat(val).as(caseString).isEqualTo(result);
		}
	}

	@Test
	public void testPizzaOnlyNoReviewProcess() {
		Map<String, Object> data = new HashMap<>();
		data.put("pizza", "yes");
		data.put("drinks", "null");
		data.put("review", "null");

		assertProcessRunsTasks(App.KP_PROCESS, data, "OrderPizzaDrinksTask", "CreateOrderTask", "PreparePizzaTask",
				"OfferMealTask", "PayMealTask", "EatTask", "WriteReviewTask");
	}

	/**
	 * Asserts that a newly started process instance will finish if these user tasks
	 * are completed in the given order.
	 */
	private void assertProcessRunsTasks(String processKey, Map<String, Object> data, String... taskKeys) {
		ProcessInstance proc = engine().getRuntimeService().startProcessInstanceByKey(processKey);
		for (String taskKey : taskKeys) {
			TaskQuery createTaskQuery = engine().getTaskService().createTaskQuery();
			List<Task> list = createTaskQuery.taskDefinitionKey(taskKey).list();

			createTaskQuery = engine().getTaskService().createTaskQuery();
			list = createTaskQuery.taskDefinitionKey(taskKey).list();

			Optional<Task> task = list.stream().filter( //
					x -> x.getTaskDefinitionKey().equals(taskKey)).findFirst();

			assertThat(task.isPresent()).as("expected " + taskKey + ", actual " + toString(list)).isTrue();

			engine().getTaskService().complete(task.get().getId(), data);
		}
		assertThat(proc).isEnded();
	}

	/** Returns a string with the definition keys of the given tasks. */
	private String toString(List<Task> list) {
		return Arrays.deepToString(list.stream().map(x -> x.getTaskDefinitionKey()).toArray());
	}
}
