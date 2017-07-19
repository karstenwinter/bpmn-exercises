package kp.app;

import java.util.Map;
import java.util.TreeMap;

class ExecData {
	public String procId;
	public String taskId;
	public Map<String, String> actions = new TreeMap<>();
	public Map<String, Object> data = new TreeMap<>();
}
