package markov;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BidirectionalVariableOrderPrefixIDMap<T extends Token> {

	private int order;
	private int nextID = 0;
	
	@SuppressWarnings("unchecked")
	public BidirectionalVariableOrderPrefixIDMap(int order) {
		this.order = order;
	}
	
	public int getOrder() {
		return this.order;
	}

	public int getPrefixCount() {
		return nextID;
	}

	private Map<LinkedList<Token>, Integer> prefixToIDMap = new HashMap<LinkedList<Token>, Integer>();
	private List<LinkedList<Token>> iDToPrefixMap = new ArrayList<LinkedList<Token>>(prefixToIDMap.size());
	
	/**
	 * 
	 * @param prefix
	 * @return Integer ID associated with prefix in this Map
	 */
	public Integer addPrefix(LinkedList<Token> prefix) {
		if(prefix.size() != order) {
			throw new RuntimeException("Tried to add prefix \"" + prefix + "\" that does not match order of " + order);
		}
		
		Integer id = prefixToIDMap.get(prefix);
		if (id == null) {
			id = nextID++;
			final LinkedList<Token> prefixCopy = new LinkedList<Token>(prefix);
			prefixToIDMap.put(prefixCopy, id);
			iDToPrefixMap.add(prefixCopy);
		}
		
		return id;
	}
	
	public Integer getIDForPrefix(LinkedList<Token> prefix) {
		return prefixToIDMap.get(prefix);
	}

	@SuppressWarnings("unchecked")
	public T getPrefixFinaleForID(int toStateIdx) {
		return (T) getPrefixForID(toStateIdx).getLast();
	}
	
	public LinkedList<Token> getPrefixForID(int toStateIdx) {
		return getIDToPrefixMap().get(toStateIdx);
	}

	public List<LinkedList<Token>> getIDToPrefixMap() {
		return iDToPrefixMap;
	}
}
