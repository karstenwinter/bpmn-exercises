package kp.app;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.springframework.stereotype.Service;

/**
 * On execution, this class logs a short message.
 * 
 * @author broxp
 */
@Service("logger")
public class LoggerDelegate implements JavaDelegate {
	private static final Log log = LogFactory.getLog(LoggerDelegate.class);

	@Override
	public void execute(DelegateExecution execution) throws Exception {
		FlowElement modelElem = execution.getBpmnModelElementInstance();
		log.debug("logging " + modelElem.getName());
	}
}