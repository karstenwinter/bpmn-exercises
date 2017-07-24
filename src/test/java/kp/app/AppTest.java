package kp.app;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.camunda.bpm.dmn.engine.DmnDecisionResult;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.dmn.DecisionsEvaluationBuilder;
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
 * Remark: Inheriting from {@link ProcessEngineAssertions} is not necessary but
 * enables easier Organise imports.
 * 
 * @author broxp
 */
@Deployment(resources = { App.BPMN_FILE, App.DMN_FILE })
public class AppTest extends ProcessEngineAssertions {
	@ClassRule
	public static ProcessEngineRule rule = TestCoverageProcessEngineRuleBuilder.create()
			.build();

	private String processModelId;
	private BpmnModelInstance model;

	/**
	 * Deploys the process and decision (manually), retrieves id and model for
	 * further use.
	 */
	@Before
	public void setUp() throws Exception {
		ProcessEngineAssertions.init(engine());
		Mocks.register("sendMessage", new SendMessage());
		Mocks.register("logger", new LoggerDelegate());

		RepositoryService repositoryService = engine().getRepositoryService();
		DeploymentWithDefinitions result = App.deployFiles(repositoryService);
		assertThat(result.getDeployedProcessDefinitions()).isNotEmpty();

		processModelId = result.getDeployedProcessDefinitions().get(0).getId();
		model = repositoryService.getBpmnModelInstance(processModelId);
		assertThat(model).isNotNull();
	}

	@Test
	public void testFullProcess() {
		Map<String, Object> data = new HashMap<>();
		data.put("pizza", "yes");
		data.put("drinks", "yes");
		data.put("review", "yes");

		assertProcessRunsTasks(App.KP_PROCESS, data, "OrderPizzaDrinksTask",
				"CreateOrderTask", "PreparePizzaTask", "PrepareDrinksTask",
				"OfferMealTask", "EatTask", "DrinkTask", "PayMealTask", "WriteReviewTask",
				"ProcessReviewTask");
	}

	@Test
	public void testShortestProcess() {
		Map<String, Object> data = new HashMap<>();
		data.put("pizza", "");
		data.put("drinks", "");
		data.put("review", "");

		assertProcessRunsTasks(App.KP_PROCESS, data, "OrderPizzaDrinksTask",
				"CreateOrderTask");
	}

	@Test
	public void testDrinksOnlyWithReviewProcess() {
		Map<String, Object> data = new HashMap<>();
		data.put("pizza", "");
		data.put("drinks", "yes");
		data.put("review", "yes");

		assertProcessRunsTasks(App.KP_PROCESS, data, "OrderPizzaDrinksTask",
				"CreateOrderTask", "PrepareDrinksTask", "OfferMealTask", "DrinkTask",
				"PayMealTask", "WriteReviewTask", "ProcessReviewTask");
	}

	@Test
	public void testPizzaOnlyNoReviewProcess() {
		Map<String, Object> data = new HashMap<>();
		data.put("pizza", "yes");
		data.put("drinks", "");
		data.put("review", "");

		assertProcessRunsTasks(App.KP_PROCESS, data, "OrderPizzaDrinksTask",
				"CreateOrderTask", "PreparePizzaTask", "OfferMealTask", "EatTask",
				"PayMealTask", "WriteReviewTask");
	}

	/**
	 * JUnit's Parameterized runner is possible but over-engineering at this point.
	 */
	@Test
	public void testDecisions1() {
		String[][] triples = { //
				// pizza, drinks, review
				{ "salami", "cola", "Yummy salami pizza and cola." }, //
				{ "salami", "", "Yummy salami pizza." }, //
				{ "", "cola", "Great cola." }, //
				{ "", "other", "Quite ok." }, //
				{ "other", "", "Quite ok." }, //
				{ "other", "other", "Quite ok." }, //
				{ "", "", "" }, //
		};

		assertDecisionCases(App.KP_DECISION, triple -> {
			Map<String, Object> data = new HashMap<>();
			data.put("pizza", triple[0]);
			data.put("drinks", triple[1]);
			return data;
		}, t -> t[2], triples);
	}

	private void assertDecisionCases(String dmnKey,
			Function<String[], Map<String, Object>> input,
			Function<String[], String> output, String[][] data) {
		for (String[] triple : data) {
			Map<String, Object> inputCase = input.apply(triple);
			String outputCase = output.apply(triple);

			DecisionsEvaluationBuilder dmn = engine().getDecisionService()
					.evaluateDecisionByKey(dmnKey);

			String caseString = Arrays.deepToString(triple);

			DmnDecisionResult res = dmn.variables(inputCase).evaluate();
			assertThat(res).as(caseString).isNotEmpty();
			String val = res.getSingleEntry();
			assertThat(val).as(caseString).isEqualTo(outputCase);
		}
	}

	/**
	 * Asserts that a newly started process instance will finish if these user tasks
	 * are completed in the given order.
	 */
	private void assertProcessRunsTasks(String processKey, Map<String, Object> data,
			String... orderedTaskKeys) {
		ProcessInstance proc = engine().getRuntimeService()
				.startProcessInstanceByKey(processKey);
		for (String taskKey : orderedTaskKeys) {
			TaskQuery createTaskQuery = engine().getTaskService().createTaskQuery();
			List<Task> list = createTaskQuery.list();

			List<String> taskKeys = list.stream().map(x -> x.getTaskDefinitionKey())
					.collect(Collectors.toList());

			assertThat(taskKeys).contains(taskKey);

			// for parallel parts in the model: filter -> find first
			Optional<Task> task = list.stream()
					.filter(x -> x.getTaskDefinitionKey().equals(taskKey)).findFirst();

			engine().getTaskService().complete(task.get().getId(), data);
		}
		assertThat(proc).isEnded();
	}

	private ProcessEngine engine() {
		return rule.getProcessEngine();
	}

}
