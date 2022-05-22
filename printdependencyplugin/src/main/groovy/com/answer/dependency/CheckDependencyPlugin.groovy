package com.answer.dependency

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector

class CheckDependencyPlugin implements Plugin<Project> {

    ElementNode root = new ElementNode()
    Map<String, ElementNode> treeMap = new HashMap<String, ElementNode>()

    void apply(Project project) {
        if (System.properties['enableCheckModuleDependency'] != 'true') {
            return
        }
        project.gradle.addBuildListener(new BuildListener() {
            @Override
            void buildStarted(Gradle gradle) {
            }

            @Override
            void settingsEvaluated(Settings settings) {
            }

            @Override
            void projectsLoaded(Gradle gradle) {
            }

            @Override
            void projectsEvaluated(Gradle gradle) {
            }

            @Override
            void buildFinished(BuildResult buildResult) {
                generateMarkDownResult()
            }
        })
        project.gradle.allprojects(new Action<Project>() {
            @Override
            void execute(Project projecta) {
                projecta.gradle.taskGraph.addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
                    @Override
                    void graphPopulated(TaskExecutionGraph taskExecutionGraph) {
                        checkDependence(projecta)
                    }
                })
            }
        })
    }

    private void checkDependence(Project project) {
        def variants
        if (project.plugins.hasPlugin('com.android.application')) {
            root = collectNode(project.name)
            variants = project.android.applicationVariants
        } else if (project.plugins.hasPlugin('com.android.library')) {
            variants = project.android.libraryVariants
        } else {
            return
        }
        variants.all { variant ->
            Configuration configuration
            try {
                configuration = project.configurations."${variant.name}CompileClasspath"
            } catch (Exception e) {
                configuration = project.configurations."_${variant.name}Compile"
            }
            def dependencies = configuration.getIncoming()
                    .getResolutionResult()
                    .getRoot()
                    .getDependencies()
            dependencies.each {
                ComponentSelector requested = it.getRequested()
                if (requested instanceof DefaultProjectComponentSelector) {
                    ElementNode currentNode = collectNode(project.name)
                    Project child = project.rootProject.project(requested.projectPath)
                    if (child != null) {
                        ElementNode childNode = collectNode(child.name)
                        if (currentNode.dependencies.find {
                            it.moduleName == child.name
                        } == null) {
                            currentNode.dependencies.add(childNode)
                        }
                    }
                }
            }
        }
    }

    private ElementNode collectNode(String moduleName) {
        def currentNode
        if (treeMap.containsKey(moduleName)) {
            currentNode = treeMap.get(moduleName)
        } else {
            currentNode = new ElementNode()
            currentNode.moduleName = moduleName
            treeMap.put(moduleName, currentNode)
        }

        return currentNode
    }


//输出markdown格式的文本信息
    private void generateMarkDownResult() {
        println("treeMap.size : " + treeMap.values().size())
        for (ElementNode element in treeMap.values()) {
            removeUnnecessaryDependency(element)
        }
        println("```mermaid")
        def head = "graph TD"
        println(head)
        for (ElementNode element in treeMap.values()) {
            def currentName = element.moduleName
            for (ElementNode childElement in element.dependencies) {
                println("${currentName}[${currentName}]-->${childElement.moduleName}[${childElement.moduleName}]")
            }
        }
        println("```")
    }

/**
 * 优化：移除不需要的依赖，原则：currentNode的孩子队列，最短依赖，有可以被非最短依赖替代的，则删除最短依赖。
 */
    private void removeUnnecessaryDependency(ElementNode currentNode) {
        def dependencyList = currentNode.dependencies
        //为了避免remove失败，创建了一个temp list
        List<ElementNode> tempDependencyList = new ArrayList<>(dependencyList)
        def iterator = tempDependencyList.iterator()
        while (iterator.hasNext()) {
            ElementNode targetElement = iterator.next()
            List<ElementNode> leftTargetElements = tempDependencyList.findAll({
                it != targetElement
            })
            for (ElementNode otherItem in leftTargetElements) {
                if (containNode(otherItem, targetElement)) {
                    dependencyList.remove(targetElement)
                    continue
                }
            }
        }
    }

/**
 * rootNode下面是否包含targetNode结点
 */
    private boolean containNode(ElementNode rootNode, ElementNode targetNode) {
        if (rootNode == targetNode) {
            return true
        }
        def rootChildren = rootNode.dependencies
        for (ElementNode child in rootChildren) {
            if (rootNode == child) {
                return true
            } else {
                def result = containNode(child, targetNode)
                if (result) {
                    return result
                }
            }
        }
        return false
    }

}