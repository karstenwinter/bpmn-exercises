package kp.app;

import java.util.Collection;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.springframework.stereotype.Service;

/**
 * On execution, this class sends the boundary signal defined in the model if
 * the Data Object this class is attached to is set.
 * 
 * @author broxp
 */
@Service("messageOnVariable")
public class MessageOnVariable implements JavaDelegate {
	private static final Log log = LogFactory.getLog(MessageOnVariable.class);

	@Override
	public void execute(DelegateExecution execution) throws Exception {
		log.debug("signaling...");
		try {
			FlowElement modelElem = execution.getBpmnModelElementInstance();
			if (!(modelElem instanceof Activity)) {
				log.error(
						"this class must be attached to a Activity, " + modelElem.getId()
								+ " is a " + modelElem.getClass().getSimpleName());
				return;
			}

			Activity activity = (Activity) modelElem;
			MessageEventDefinition message = getMessageEventWithSignal(modelElem,
					activity);
			if (message == null) {
				return;
			}

			log.debug("found " + message);

			String messageName = message.getMessage().getName();
			processMessageEvent(execution, modelElem, activity, messageName);
		} catch (Exception e) {
			log.error("", e);
			throw e;
		}
	}

	/**
	 * Returns a message event that was used as a boundary event for the given model
	 * element. Returns {@code null} if the message event is not found or the
	 * message event doesn't have a message defined.
	 */
	private MessageEventDefinition getMessageEventWithSignal(FlowElement modelElem,
			Activity activity) {
		MessageEventDefinition message = null;
		BoundaryEvent boundaryEvent = null;
		for (BoundaryEvent ev : modelElem.getModelInstance()
				.getModelElementsByType(BoundaryEvent.class)) {
			if (ev.getAttachedTo().equals(activity)) {
				boundaryEvent = ev;
				Collection<EventDefinition> eventDefinitions = ev.getEventDefinitions();
				for (EventDefinition eventDefinition : eventDefinitions) {
					if (eventDefinition instanceof MessageEventDefinition) {
						message = (MessageEventDefinition) eventDefinition;
						break;
					}
				}
				break;
			}
		}

		if (boundaryEvent == null) {
			log.error("no boundary event attached to " + modelElem.getId());
			return null;
		}

		if (message == null || message.getMessage() == null) {
			log.error(
					"no signal event with a signal attached to " + boundaryEvent.getId());
			message = null;
		}
		return message;
	}

	/**
	 * Searches for data inputs, checks if variables are present and sends the
	 * message if the variable value is non empty.
	 */
	@SuppressWarnings("rawtypes")
	private void processMessageEvent(DelegateExecution execution, FlowElement modelElem,
			Activity activity, String messageName) {
		RuntimeService runtimeService = execution.getProcessEngineServices()
				.getRuntimeService();

		Collection<DataInputAssociation> dataInputs = activity.getDataInputAssociations();
		if (dataInputs.isEmpty()) {
			log.error("no data input attached to" + modelElem.getId());
			return;
		}

		for (DataInputAssociation dataInputAssociation : dataInputs) {
			DataObjectReference target = (DataObjectReference) dataInputAssociation
					.getSources().iterator().next();
			Object variable = execution.getVariable(target.getName());
			if (variable instanceof Collection && ((Collection) variable).isEmpty()) {
				continue;
			}
			if (variable != null && !"".equals(variable)) {
				log.error("message name " + messageName);
				runtimeService.correlateMessage(messageName, new HashMap<>(),
						execution.getVariables());
			}
		}
	}
}