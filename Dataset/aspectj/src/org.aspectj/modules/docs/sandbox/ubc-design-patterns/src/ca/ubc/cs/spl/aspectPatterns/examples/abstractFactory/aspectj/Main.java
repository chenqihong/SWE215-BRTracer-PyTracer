package ca.ubc.cs.spl.aspectPatterns.examples.abstractFactory.aspectj;

/* -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This file is part of the design patterns project at UBC
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * either http://www.mozilla.org/MPL/ or http://aspectj.org/MPL/.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is ca.ubc.cs.spl.aspectPatterns.
 * 
 * For more details and the latest version of this code, please see:
 * http://www.cs.ubc.ca/labs/spl/projects/aodps.html
 *
 * Contributor(s):   
 */

import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.JButton;
import javax.swing.JPanel;   
import javax.swing.ButtonGroup;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


/**
 * Implements a driver for the Abstract Factory design pattern example.
 * The pattern intent is:
 * <blockquote>
 * Provide an interface for creating families of related or dependent objects
 * without specifying their concrete classes.
 * </blockquote>
 *
 * As an example scenario, our abstract factory interface defines two
 * factory methods, <code>createLabel()</code> and 
 * <code>createButton()</code>, that create related products 
 * (here: Swing GUI elements). 
 *
 * The driver is a GUI that allows the user to choose between the two 
 * concrete factories (<code>RegularFactory</code> and <code>FramedFactory
 * </code>), and creates a new GUI with elements from the respective factory.
 * <p>
 * 
 * <code>RegularFactory</code> creates standard Swing GUI elements, while
 * <code>FramedFactory</code> produces elements which are framed.
 *
 * <P><i>This is the AspectJ implementation. </i><p>
 *
 * We decided to implement <code>AbstractFactory</code> as an interace,
 * not an abstract class. In the AspectJ solution, we can still define 
 * default implementations for the interface methods. This approach uses
 * AspectJ's inter-type declaration mechanism.
 *
 * To illustrate this, the aspect <code>AbstratFactoryEnhancement</code>
 * attaches a default implementation of both factory methods to all
 * classes that implement the <code>AbstractFactory</code> interface.
 * This default implementation creates regular Swing elements and is
 * used by <code>RegularFactory</code>.
 *
 * @author Jan Hannemann
 * @author Gregor Kiczales
 * @version 1.1, 01/20/04
 *
 */
public class Main 
{
    /**
     * a concrete factory that creates regular GUI components
     */
     
	private static ComponentFactory factory1 = new RegularFactory();
	
    /**
     * a concrete factory that creates framed GUI components
     */
     
	private static ComponentFactory factory2 = new FramedFactory();

    /**
     * stores the currently selected factory
     */
     
	private static ComponentFactory factory = factory1;
	
	
    /**
     * Creates the initial GUI that allows the user to choose a factory
     * and generate a new GUI with the elements that the respective
     * factory provides.
     *
     * @return a <code>JPanel</code> containing the GUI
     */
     
	private static JPanel createGUI()
	{
		ActionListener radioListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (e.getActionCommand().equals("factory1")) factory = factory1;
				else factory = factory2;
			}
		};
		
		JPanel panel = new JPanel();
		JRadioButton factoryButton1 = new JRadioButton("use Factory 1"); 
		JRadioButton factoryButton2 = new JRadioButton("use Factory 2");
		factoryButton1.setActionCommand("factory1");
		factoryButton2.setActionCommand("factory2");		
		factoryButton1.addActionListener(radioListener);
		factoryButton2.addActionListener(radioListener);		
		JButton create = new JButton("Create GUI");
		
		ButtonGroup choices = new ButtonGroup();
		
		choices.add(factoryButton1);
		choices.add(factoryButton2);
		
		create.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) { 
				Display display = new Display(factory);
			}
		});
		
		panel.add(factoryButton1);
		panel.add(factoryButton2);
		panel.add(create);
		
		return panel;
	}

    /**
     * Implements the driver for this design pattern example. It sets up
     * the initial GUI.
     */				
		
	public static void main(String[] args)
	{
		JFrame frame = new JFrame("Abstract Factory Demo");
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {System.exit(0);}
		});  
		
		frame.getContentPane().add(createGUI());
		
		frame.pack();
		frame.setVisible(true);
	}
}