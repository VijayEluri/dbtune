package edu.ucsc.satuning.ibg;

import edu.ucsc.satuning.ibg.IndexBenefitGraph.IBGChild;
import edu.ucsc.satuning.ibg.IndexBenefitGraph.IBGNode;
import edu.ucsc.satuning.util.Stack;

public class IBGNodeStack {
	private Stack<Object> stack = new Stack<Object>();

    // add child and all siblings (following next pointers) to the stack
	final void addChildren(IBGChild ch) {
		if (ch != null) stack.push(ch);
	}
	
	final void addNode(IBGNode node) {
		stack.push(node);
	}
	
	final boolean hasNext() {
		return !stack.isEmpty();
	}
	
	final void reset() {
		stack.popAll();
	}
	
	final IBGNode next() {
		if (stack.isEmpty())
			return null;
		
		Object obj = stack.peek();
		if (obj instanceof IBGChild) {
			IBGChild child = (IBGChild) stack.peek();
			if (child.next != null)
				stack.swap(child.next);
			else 
				stack.pop();
			return child.node;
		}
		else {
			stack.pop();
			return (IBGNode) obj;
		}
	}
}
