package kp.app;

import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.camunda.bpm.model.bpmn.instance.ThrowEvent;
import org.springframework.stereotype.Service;

/**
 * On execution, this class sends the message defined in the {@link ThrowEvent}
 * model this class is attached to.
 * 
 * @author broxp
 */
@Service("sendMessage")
public class SendMessage implements JavaDelegate {
	private static final Log log = LogFactory.getLog(SendMessage.class);

	@Override
	public void execute(DelegateExecution execution) throws Exception {
		log.debug("sending message...");
		try {
			FlowElement modelElem = execution.getBpmnModelElementInstance();
			if (!(modelElem instanceof ThrowEvent)) {
				log.error("this class must be attached to a ThrowEvent, " + modelElem.getId() + " is a "
						+ modelElem.getClass().getSimpleName());
				return;
			}
			ThrowEvent event = (ThrowEvent) modelElem;
			Collection<EventDefinition> eventDefinitions = event.getEventDefinitions();
			if (eventDefinitions.isEmpty()) {
				log.debug("no messages defined in " + event.getId());
			}
			MessageEventDefinition m = (MessageEventDefinition) eventDefinitions.iterator().next();
			String name = m.getMessage().getName();
			log.debug("message name: " + name);
			RuntimeService runtimeService = execution.getProcessEngineServices().getRuntimeService();
			runtimeService.correlateMessage(name, name);
		} catch (Exception e) {
			log.error("", e);
			throw e;
		}
	}
}