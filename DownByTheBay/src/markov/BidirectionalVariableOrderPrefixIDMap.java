package markov;

import java.util.LinkedList;
import java.util.List;

public abstract class BidirectionalVariableOrderPrefixIDMap<T extends Token> {

	protected int order;
	protected int nextID = 0;
	
	@SuppressWarnings("unchecked")
	public BidirectionalVariableOrderPrefixIDMap(int order) {
		this.order = order;
	}
	
	/**
	 * 
	 * @param prefix
	 * @return Integer ID associated with prefix in this Map
	 */
	public abstract Integer addPrefix(LinkedList<Token> prefix);
	
	public abstract Integer getIDForPrefix(LinkedList<Token> prefix);

	@SuppressWarnings("unchecked")
	public abstract T getPrefixFinaleForID(int toStateIdx);
	
	@SuppressWarnings("unchecked")
	public abstract LinkedList<Token> getPrefixForID(int toStateIdx);

	public int getOrder() {
		return this.order;
	}

	public abstract List<LinkedList<Token>> getIDToPrefixMap();

	public int getPrefixCount() {
		return nextID;
	}
}
