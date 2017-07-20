package kp.app;

/**
 * Data container for the view model.
 * 
 * @author broxp
 */
public class ViewModelData {
	public String title;
	public String uri;
	public String info;
	public String options;

	/** Attributes of the {@link ViewModelData} */
	public enum Attr {
		title, uri, info, options
	}
}