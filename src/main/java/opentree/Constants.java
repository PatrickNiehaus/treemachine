package opentree;

public enum Constants {

	DRAFTTREENAME (String.class, "otol.draft.22");
	
    public final Class<?> type;
    public final Object value;

	Constants(Class<?> type, Object value) {
		this.type = type;
		this.value = value;
	}
}
