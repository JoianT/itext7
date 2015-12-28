package com.itextpdf.forms.xfa;

import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Processes the datasets section in the XFA form.
 */
public class Xml2SomDatasets extends Xml2Som {
    /**
     * Creates a new instance from the datasets node. This expects
     * not the datasets but the data node that comes below.
     *
     * @param n the datasets node
     */
    public Xml2SomDatasets(Node n) {
        order = new ArrayList<String>();
        name2Node = new HashMap<String, Node>();
        stack = new Stack2<String>();
        anform = 0;
        inverseSearch = new HashMap<String, InverseStore>();
        processDatasetsInternal(n);
    }

    /**
     * Inserts a new <CODE>Node</CODE> that will match the short name.
     *
     * @param n         the datasets top <CODE>Node</CODE>
     * @param shortName the short name
     * @return the new <CODE>Node</CODE> of the inserted name
     */
    public Node insertNode(Node n, String shortName) {
        Stack2<String> stack = splitParts(shortName);
        org.w3c.dom.Document doc = n.getOwnerDocument();
        Node n2 = null;
        n = n.getFirstChild();
        while (n.getNodeType() != Node.ELEMENT_NODE)
            n = n.getNextSibling();
        for (int k = 0; k < stack.size(); ++k) {
            String part = stack.get(k);
            int idx = part.lastIndexOf('[');
            String name = part.substring(0, idx);
            idx = Integer.parseInt(part.substring(idx + 1, part.length() - 1));
            int found = -1;
            for (n2 = n.getFirstChild(); n2 != null; n2 = n2.getNextSibling()) {
                if (n2.getNodeType() == Node.ELEMENT_NODE) {
                    String s = escapeSom(n2.getLocalName());
                    if (s.equals(name)) {
                        ++found;
                        if (found == idx)
                            break;
                    }
                }
            }
            for (; found < idx; ++found) {
                n2 = doc.createElementNS(null, name);
                n2 = n.appendChild(n2);
                Node attr = doc.createAttributeNS(XfaForm.XFA_DATA_SCHEMA, "dataNode");
                attr.setNodeValue("dataGroup");
                n2.getAttributes().setNamedItemNS(attr);
            }
            n = n2;
        }
        inverseSearchAdd(inverseSearch, stack, shortName);
        name2Node.put(shortName, n2);
        order.add(shortName);
        return n2;
    }

    private static boolean hasChildren(Node n) {
        Node dataNodeN = n.getAttributes().getNamedItemNS(XfaForm.XFA_DATA_SCHEMA, "dataNode");
        if (dataNodeN != null) {
            String dataNode = dataNodeN.getNodeValue();
            if ("dataGroup".equals(dataNode))
                return true;
            else if ("dataValue".equals(dataNode))
                return false;
        }
        if (!n.hasChildNodes())
            return false;
        Node n2 = n.getFirstChild();
        while (n2 != null) {
            if (n2.getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
            n2 = n2.getNextSibling();
        }
        return false;
    }

    private void processDatasetsInternal(Node n) {
        if (n != null) {
            HashMap<String, Integer> ss = new HashMap<String, Integer>();
            Node n2 = n.getFirstChild();
            while (n2 != null) {
                if (n2.getNodeType() == Node.ELEMENT_NODE) {
                    String s = escapeSom(n2.getLocalName());
                    Integer i = ss.get(s);
                    if (i == null)
                        i = Integer.valueOf(0);
                    else
                        i = Integer.valueOf(i.intValue() + 1);
                    ss.put(s, i);
                    if (hasChildren(n2)) {
                        stack.push(s + "[" + i.toString() + "]");
                        processDatasetsInternal(n2);
                        stack.pop();
                    } else {
                        stack.push(s + "[" + i.toString() + "]");
                        String unstack = printStack();
                        order.add(unstack);
                        inverseSearchAdd(unstack);
                        name2Node.put(unstack, n2);
                        stack.pop();
                    }
                }
                n2 = n2.getNextSibling();
            }
        }
    }
}