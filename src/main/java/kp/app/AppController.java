package kp.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * User interaction happens here.
 * 
 * @author broxp
 */
@Controller
class AppController {
	private static final Log log = LogFactory.getLog(AppController.class);
	@Autowired
	ProcessEngine engine;

	/** Starts a new process instance. */
	@GetMapping
	@RequestMapping("/start")
	public ModelAndView start(@RequestParam Map<String, Object> data) {
		log.debug("start");
		engine.getRuntimeService().startProcessInstanceById(App.getProcessId());
		String title = "started process " + App.getProcessId();
		return getModel(title, data);
	}

	/** Completes a task. */
	@GetMapping
	@RequestMapping("/complete")
	public ModelAndView complete(@RequestParam String procId, @RequestParam String taskId,
			@RequestParam Map<String, Object> data) {
		log.debug("complete");
		String title = "";

		TaskQuery createTaskQuery = engine.getTaskService().createTaskQuery();
		List<Task> tasks = createTaskQuery.processInstanceId(procId).list();
		if (tasks.isEmpty()) {
			title = "no task in execution " + procId;
		} else {
			BpmnModelInstance model = engine.getRepositoryService().getBpmnModelInstance(App.getProcessId());

			for (Task task : tasks) {
				if (task.getTaskDefinitionKey().equals(taskId)) {
					title = completeOrFail(procId, taskId, data, model, task);
					break;
				} else {
					title = "execution had task " + task.getTaskDefinitionKey() + ", not " + taskId;
				}
			}
		}
		return getModel(title, data);
	}

	/** User interface for an unknown command. */
	@GetMapping
	@RequestMapping("/*")
	public ModelAndView unknown(HttpServletRequest req, @RequestParam Map<String, Object> data) {
		log.debug("list");
		String requestURI = req.getRequestURI();
		if (requestURI.startsWith("/")) {
			requestURI = requestURI.substring(1);
		}
		return getModel("Unknown command: " + requestURI, data);
	}

	/**
	 * Tries to complete the given task. Returns the exception string representation
	 * if an error occurred.
	 */
	private String completeOrFail(String procId, String taskId, Map<String, Object> data, BpmnModelInstance model,
			Task task) {
		try {
			engine.getTaskService().complete(task.getId(), data);

			String taskDefinitionKey = task.getTaskDefinitionKey();
			String target = ((FlowNode) model.getModelElementById(taskDefinitionKey)) //
					.getOutgoing().stream() //
					.map(mod -> mod.getTarget().getId()) //
					.collect(Collectors.toList()) //
					.toString();

			return "completed task " + taskId + " in execution " + procId + (target.isEmpty() ? "" : (" to " + target));
		} catch (Exception e) {
			log.error("", e);
			return e.toString();
		}
	}

	/** Returns the {@link ModelAndView} for this app. */
	private ModelAndView getModel(String title, Map<String, Object> data) {
		log.debug(title);
		ViewModelData model = new ViewModelData();
		model.title = title;
		model.info = infoOutput(data);
		model.options = "<a href='start'>start</a>";
		return new HtmlModelAndView("templates/items.html", model);
	}

	/** Generates HTML output for the processes and a form to enter values. */
	private String infoOutput(Map<String, Object> data) {
		if (engine.getRepositoryService().createDeploymentQuery().count() == 0) {
			return "";
		}
		List<ProcessInstanceData> res = executions(data);
		StringBuilder str = new StringBuilder();
		str.append("<table><tr><th>Process Instance</th><th>Task</th><th>Data</th></tr>\n");
		boolean showData = true;
		for (ProcessInstanceData execution : res) {
			str.append("<tr><td>").append(execution.procId).append("</td><td>");
			for (Entry<String, String> ac : execution.actions.entrySet()) {
				str.append(ac.getValue() + " (" + ac.getKey() + ")").append("<br />");
			}
			str.append("</td><td>");
			if (showData) {
				str.append("<form action=\"complete\" method=\"get\">");
				for (Entry<String, Object> ac : execution.data.entrySet()) {
					str.append(ac.getKey() + ": <input name=\"" + ac.getKey() + "\" type=\"text\" value=\""
							+ ac.getValue() + "\"></input><br />");
				}
				str.append("<br /><input type=\"submit\" /><form>");
				showData = false;
			} else {
				str.append("---");
			}
			str.append("</td></tr>\n");
		}
		str.append("</table>\n");
		return str.toString();
	}

	/** Returns the running processes, grouped by process instance */
	private List<ProcessInstanceData> executions(Map<String, Object> data) {
		BpmnModelInstance bpmn = engine.getRepositoryService().getBpmnModelInstance(App.getProcessId());
		List<Execution> executions = engine.getRuntimeService().createExecutionQuery().list();

		List<ProcessInstanceData> res = new ArrayList<>();

		Map<String, List<Execution>> grouped = executions.stream() //
				.collect(Collectors.groupingBy(a -> a.getProcessInstanceId()));

		for (Entry<String, List<Execution>> group : grouped.entrySet()) {
			ProcessInstanceData executionData = new ProcessInstanceData();
			res.add(executionData);
			executionData.procId = group.getKey();

			for (Execution execution : group.getValue()) {
				String procId = execution.getProcessInstanceId();

				Map<String, Object> variables = engine.getRuntimeService().getVariables(procId);
				for (Entry<String, Object> entry : variables.entrySet()) {
					Object value = unwrapValue(entry.getValue());
					log.debug(value == null ? "--> null" : "--> " + value + " : " + value.getClass());
					executionData.data.put(entry.getKey(), String.valueOf(value));
				}

				List<String> activityIds = engine.getRuntimeService().getActiveActivityIds(procId);
				for (String act : activityIds) {
					ModelElementInstance elem = bpmn.getModelElementById(act);
					fillExecData(data, executionData, procId, elem);
				}
			}
		}
		return res;
	}

	/** Unwraps a value wrapped by the Camunda framework. */
	@SuppressWarnings("unchecked")
	private Object unwrapValue(Object value) {
		if (value instanceof TypedValue) {
			return ((TypedValue) value).getValue();
		} else if (value instanceof Map) {
			return ((Map<String, Object>) value).entrySet().iterator().next().getValue();
		}
		return value;
	}

	/** Fills data into this {@link ProcessInstanceData}. */
	private void fillExecData(Map<String, Object> data, ProcessInstanceData executionData, String procId,
			ModelElementInstance elem) {
		if (!(elem instanceof Activity)) {
			return;
		}
		Activity task = (Activity) elem;

		List<String> dataNames = task.getDataOutputAssociations().stream()
				.flatMap(dataAssoc -> dataAssoc.getTarget() instanceof DataObjectReference
						? Collections.singleton((DataObjectReference) dataAssoc.getTarget()).stream()
						: null)
				.map(x -> x.getName()).collect(Collectors.toList());

		for (String string : dataNames) {
			if (!executionData.data.containsKey(string)) {
				executionData.data.put(string, "null");
			}
		}

		executionData.data.putAll(data);
		executionData.data.put("procId", procId);
		executionData.data.put("taskId", task.getId());
		executionData.taskId = task.getId();
		TaskService taskService = engine.getTaskService();
		TaskQuery query = taskService.createTaskQuery();
		long count = query.processInstanceId(procId).taskDefinitionKey(task.getId()).count();
		if (count > 0) {
			executionData.actions.put(task.getId(), task.getName());
		}
	}
}