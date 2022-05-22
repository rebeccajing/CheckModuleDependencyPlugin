package com.answer.dependency

class ElementNode {
    String moduleName
    ArrayList<ElementNode> dependencies = new ArrayList<>()

    ElementNode() {}
}