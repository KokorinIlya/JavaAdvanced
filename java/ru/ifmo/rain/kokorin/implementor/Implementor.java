package ru.ifmo.rain.kokorin.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;

/**
 * This class creates Impl-classes - implementations of classes and interfaces, that user provides.
 * Class offers user
 * <ul>
 *     <li>to create .lava files with source codes of implementations</li>
 *     <li>to compile them and pack compiled .class into Jar-archives </li>
 * </ul>
 *
 *
 * @author Ilya Kokorin
 * @version HW 5
 * @since HW 4
 * @see Implementor#implement(Class, Path)
 * @see Implementor#implementJar(Class, Path)
 */

public class Implementor implements Impler, JarImpler {

    /**
     * Class is used to clean directory for temporary files, after creating <tt>.jar</tt>-file.
     * Used in the end of method {@link Implementor#implementJar(Class, Path)}. Recursively deletes directory, all
     * it's subdirectories and files inside. Instance of the class is passed to the
     * {@link Files#walkFileTree(Path, FileVisitor)} method.
     *
     * @see Files#walkFileTree(Path, FileVisitor)
     * @see Implementor#TEMP_DIRECTORY_CLEANER
     */
    private static class TempDirectoryCleaner extends SimpleFileVisitor<Path> {
        /**
         * Deletes file using {@link Files#delete(Path)}
         * @param file file to delete
         */
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        /**
         * Deletes directory using {@link Files#delete(Path)} method. Invoked on a directory after all of files
         * inside it and subdirectories are already deleted. Used to delete recursively directory for temporary files.
         * @param dir directory to delete
         */
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * {@code TEMP_DIRECTORY_CLEANER} is used to delete directory for temporary files in the end of
     * {@link Implementor#implementJar(Class, Path)} method.
     */
    private static final FileVisitor<Path> TEMP_DIRECTORY_CLEANER = new TempDirectoryCleaner();

    /**
     * Class used to write unicode characters to file. Unusual characters are printed with {@code '\\u'} symbol.
     * Character is considered unusual, if it's code is greater than or equal to 128. If character is usual, it is
     * printed in ordinary way, using {@link Writer#write(int)}.
     * Solves issue with different encodings (i.e. arabic).
     */
    private static class UnicodeWriter extends FilterWriter {
        /**
         * Constructor, used to create instances of class.
         * @param writer writer, used to print unicode characters.
         */
        UnicodeWriter(Writer writer) {
            super(writer);
        }

        /**
         * Prints one unicode character to file. Unusual characters are printed with {@code '\\u'} symbol.
         * Character is considered unusual, if it's code is greater than or equal to 128. If character is usual, it is
         * printed in ordinary way, using {@link Writer#write(int)}.
         * Characters are converted using {@link String#format(String, Object...)}
         * @param i character to print.
         * @throws IOException if an Output error occurs while writing.
         *
         * @see String#format(String, Object...)
         * @see java.io.FileWriter#write(String)
         */
        @Override
        public void write(int i) throws IOException {
            if (i >= 128) {
                out.write(String.format("\\u%04X", i));
            } else {
                out.write(i);
            }
        }

        /**
         * Prints part of one string to file in unicode. Every character from specified part of string
         * is converted to unicode and printed using {@link UnicodeWriter#write(int)}.
         * Characters are printed in range [offset; offset + length).
         * @param s string to print to file.
         * @param offset Offset from which to start writing characters.
         * @param length Number of characters to write.
         * @throws IOException
         * <ul>
         *     <li>if an Output error occurs while writing</li>
         *     <li>if <tt>offset</tt> is negative</li>
         *     <li>if <tt>length</tt> is negative</li>
         *     <li>if <tt>offset+length</tt> is negative</li>
         *     <li><tt>offset+length</tt> is greater than the length of the given string</li>
         * </ul>
         */
        @Override
        public void write(String s, int offset, int length) throws IOException {
            for (int i = offset; i < offset + length; i++) {
                write(s.charAt(i));
            }
        }

        /**
         * Prints one string to file in unicode. Every character from string is converted to unicode and printed using
         * {@link UnicodeWriter#write(int)}.
         * @param str string to print.
         * @throws IOException if an Output error occurs while writing.
         */
        @Override
        public void write(String str) throws IOException {
            write(str, 0, str.length());
        }
    }

    /**
     * Wrapper class for {@link java.lang.reflect.Method} for storing them in {@link java.util.HashSet}.
     * Overloads {@link MethodWrapper#hashCode()} and {@link MethodWrapper#equals(Object)} methods to store methods
     * properly.
     *
     * @see java.util.HashSet
     * @see java.lang.reflect.Method
     * @see MethodWrapper#hashCode()
     * @see MethodWrapper#equals(Object)
     */
    private static class MethodWrapper {
        /**
         * method, stored inside {@link MethodWrapper}.
         */
        private final Method method;

        /**
         * Creates exemplar of {@link MethodWrapper} with given <tt>method</tt> inside.
         * @param method method to store inside exemplar of {@link MethodWrapper}
         */
        MethodWrapper(Method method) {
            this.method = method;
        }

        /**
         * Tests, if signatures of two {@link Method} are equal. Two methods are considered equal,
         * if their signatures (names and arguments list) are the same.
         * @param obj other {@link MethodWrapper}, wrapping other method.
         * @return {@code true} if and only if names and parameters of methods are the same
         *
         * @see Object#equals(Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof MethodWrapper)) {
                return false;
            }
            MethodWrapper otherWrapper = (MethodWrapper) obj;
            return method.getName().equals(otherWrapper.method.getName()) &&
                    Arrays.equals(method.getParameterTypes(), otherWrapper.method.getParameterTypes());
        }

        /**
         * Calculates hash code of <tt>method</tt>, stored in the object. Hash code is based on method's
         * name and parameter's types.
         * @return hash code of method, stored in the object.
         *
         * @see Object#hashCode()
         */
        @Override
        public int hashCode() {
            return Arrays.hashCode(method.getParameterTypes()) * 1648 +
                    method.getName().hashCode();
        }

        /**
         * Returns {@link Method}, stored inside object.
         * @return method, stored inside object
         *
         * @see Method
         */
        Method getMethod() {
            return method;
        }
    }

    /**
     * Creates new empty instance of {@link Implementor}
     */
    public Implementor() {}

    /**
     * Type token, associated with class, that should be implemented. The class is analyzed using Java Reflection
     * API.
     * @see java.lang.reflect
     */
    private Class<?> token;

    /**
     * Writer, associated with file, where source code of Impl class should be written. All
     * methods, that prints something into file, will use this writer.
     */
    private Writer writer;

    /**
     * Main method, that will be executed.
     * This methods starts implementation, depending on arguments from command line.
     * If method is executed with <tt>-jar</tt> argument, implemented class will be packed in
     * JAR-archive. Otherwise, creates .java file with source code.
     * <br>
     * Usage: <br>
     *     <ul>
     *             <li> {@code java Implementor -jar <class-to-implement> <path-to-root>}
     *             calls {@link Implementor#implement(Class, Path)}
     *             <br>
     *             <li> {@code java Implementor <class-to-implement> <path-to-root>} calls
     *             {@link Implementor#implementJar(Class, Path)}
     *             <br>
     *     </ul>
     *
     * Where {@code class-to-implement} defines name of the class or interface to implement, and
     * {@code path-to-root} is a path to root, where .java or .jar files should be placed.
     *
     * @param args arguments for running the program from the command line.
     *
     * @see Implementor#implement(Class, Path)
     *
     */
    public static void main(String[] args) {
        if (args == null || args.length < 2 || args.length > 3 || args[0] == null) {
            System.out.println("2 or 3 arguments required: -jar <full name of class to implement> " +
                    "<path to jar file> or <full name of class to implement> <path to root directory>");
            return;
        }
        Implementor implementor = new Implementor();
        String className;
        String rootPath;

        boolean implementJar = args[0].equals("-jar");

        if (implementJar) {
            if (args.length != 3 || args[1] == null || args[2] == null) {
                System.out.println("2 arguments after -jar required: <full name of class to implement> " +
                        "<path to jar file>");
                return;
            }
            className = args[1];
            rootPath = args[2];
        } else {
            if (args.length != 2 || args[1] == null) {
                System.out.println("First argument must me -jar, otherwise, two arguments must be given " +
                    "<full name of class to implement> <path to root directory>");
            }
            className = args[0];
            rootPath = args[1];
        }
        try {
            if (implementJar) {
                implementor.implementJar(Class.forName(className), Paths.get(rootPath));
            } else {
                implementor.implement(Class.forName(className), Paths.get(rootPath));
            }
        } catch (InvalidPathException e) {
            System.out.println("Path to output file is invalid " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("Cannot find class to implement " + e.getMessage());
        } catch (ImplerException e) {
            System.out.println("Error implementing class: " + e.getMessage());
        }
    }

    /**
     * Public, protected or package-private constructor from the given class, that can be used
     * to create instances of generated class. Used as super() constructor from the constructor of
     * generated class. <tt>null</tt>, if given class is interface or there are only private constructor
     * in given class.
     */
    private Constructor nonPrivateConstructor = null;

    /**
     * Name of the generated Impl class. Formed from concatenation of name of the given class and suffix "Impl".
     */
    private String className = null;

    /**
     * Default TAB - four spaces.
     */
    private static final String TAB = "    ";

    /**
     * Line separator for the system (for example, \n).
     */
    private static final String NEWLINE = System.lineSeparator();

    /**
     * Double TAB - eight spaces.
     */
    private static final String DOUBLE_TAB = TAB + TAB;

    /**
     * Char sequence, that skips one line.
     */
    private static final String SKIP_LINE = NEWLINE + NEWLINE;

    /**
     * Checks, if there are any constructors in {@link Implementor#token}, that can be
     * accessed from subclass (public, protected or package-private).
     * If there is at least one accessible constructor, returns it (wrapped into
     * {@link Optional}), else returns empty {@code Optional}
     * @return If there is at least one constructor, that can be accessed, returns it, wrapped
     * into {@link Optional}, otherwise, returns empty {@code Optional}.
     *
     * @see Modifier#isPrivate(int)
     * @see Class#getModifiers()
     */
    private Optional<Constructor<?>> hasAccessibleConstructors() {
        return Arrays.stream(token.getDeclaredConstructors())
                .filter(constructor -> !Modifier.isPrivate(constructor.getModifiers()))
                .findAny();
    }

    /**
     * Checks, if {@link Implementor#token} can be implemented. Type token is considered incorrect, if
     * <ul>
     *     <li>specified <tt>token</tt> is final</li>
     *     <li>specified <tt>token</tt> is enum</li>
     *     <li>specified <tt>token</tt> is primitive</li>
     *     <li>specified <tt>token</tt> is array</li>
     *     <li>specified <tt>token</tt> isn't interface and contains only private constructors</li>
     * </ul>
     * If there are any accessible constructors, finds one of them using
     * {@link Implementor#hasAccessibleConstructors()} and saves it inside
     * {@link Implementor#nonPrivateConstructor} field. <br>
     *  If <tt>class token</tt> can be implemented, doesn't do anything (except finding accessible constructor,
     *  if there is at least one). Otherwise, throws {@link ImplerException} with description of why
     *  <tt>type token</tt> is considered incorrect.
     * @throws ImplerException if given token is incorrect. Cause of incorrectness is stored inside exception object.
     *
     * @see Implementor#hasAccessibleConstructors()
     * @see Implementor#nonPrivateConstructor
     */
    private void checkCorrect() throws ImplerException {
        if (token.isPrimitive()) {
            throw new ImplerException("Cannot implement primitive type");
        } else if (token.isArray()) {
            throw new ImplerException("Cannot implement array");
        } else if (token.equals(Enum.class)) {
            throw new ImplerException("Cannot implement enum");
        } else if (Modifier.isFinal(token.getModifiers())) {
            throw new ImplerException("Cannot extend final class");
        } else if (!token.isInterface()) {
            Optional<Constructor<?>> constructor = hasAccessibleConstructors();
            if (!constructor.isPresent()) {
                throw new ImplerException("Should be interface or contain at least one" +
                        " not private constructor");
            }
            nonPrivateConstructor = constructor.get();
        }
    }

    /**
     * By the given class, gets path to directory, where source code of Impl class should be created.
     * Impl class will be created in the package with name, same to the name of the {@link Implementor#token}
     * package.
     * @param root directory, in the subdirectory of which Impl class should be created.
     * @return path to directory, where Impl class should be created.
     *
     * @see Path#resolve(Path)
     * @see Class#getPackage()
     */
    private Path getPathToPackage(Path root) {
        if (token.getPackage() == null) {
            return root;
        }
        return root.resolve(token.getPackage().getName().replace(".", File.separator));
    }

    /**
     * Prints name of the package of the generated class to file with source code of the generated class. <br>
     * Impl class is stored in the package with name, same to given class package's name. <br>
     * If given class isn't located in the default package, prints concatenation of strings "package " and
     * name of the given class package name to the file with Impl class source code.
     * If given class is located in the main package, doesn't print anything.
     * Impl class will be put to package with name, same to {@link Implementor#token} package name.
     * @throws IOException if an Output error occurs while writing to file.
     *
     * @see Writer
     * @see Class#getPackage()
     */
    private void printPackage() throws IOException {
        if (token.getPackage() != null) {
            writer.write("package " + token.getPackage().getName() + ";");
            writer.write(SKIP_LINE);
        }
    }

    /**
     * Method creates String, contains method arguments, using ", " as decimeter. <br>
     * Method creates stream of objects, using given mapper function. Stream is created from numbers
     * in range [0 ... N - 1], where N is given number
     * @param length length of stream
     * @param mapper function, used to map from numbers in range [0...length - 1] to Objects
     * @return String of method arguments, concatenated by ", "
     *
     * @see IntStream#range(int, int)
     * @see IntStream#mapToObj(IntFunction)
     * @see java.util.stream.Stream#collect(Collector)
     * @see Collectors#joining(CharSequence)
     */
    private String getArgumentString(int length, IntFunction<String> mapper) {
        return IntStream
                .range(0, length)
                .mapToObj(mapper)
                .collect(Collectors.joining(" ,"));
    }

    /**
     * Method prints list of parameters of some method or constructor, using given writer. <br>
     * Method uses {@link Implementor#getArgumentString(int, IntFunction)} to create string with
     * given parameters. <br>
     * String looks like "ArgType_0 arg0, ArgType_1 arg1, ..., ArgType_n argn".
     * Canonical names of argument types are used.
     * @param parameters array of parameters of some function or constructor.
     * @throws IOException if Output error occurs while writing.
     *
     * @see Writer
     * @see Parameter
     * @see IOException
     * @see Implementor#getArgumentString(int, IntFunction)
     * @see Class#getCanonicalName()
     */
    private void printParameters(Parameter[] parameters) throws IOException {
        String argString = getArgumentString(
                parameters.length,
                i -> parameters[i].getType().getCanonicalName() + " arg" + i
        );
        writer.write(argString);
    }

    /**
     * Method prints list of exceptions of some method or constructor, using given writer. <br>
     * If array with exceptions is empty, method does not print anything. <br>
     * Else, it prints string "throws ExceptionType_1, ..., ExceptionType_n". ", " is used
     * as decimeter. Canonical names of exception types are used.
     * @param exceptions list of exceptions of some constructor or method.
     * @throws IOException if Output error occurs while writing.
     *
     * @see Class#getCanonicalName()
     */
    private void printExceptions(Class[] exceptions) throws IOException {
        if (exceptions.length == 0) {
            return;
        }
        writer.write(" throws ");
        String exceptionList = Arrays.stream(exceptions)
                .map(Class::getCanonicalName)
                .collect(Collectors.joining(", "));
        writer.write(exceptionList);
    }

    /**
     * Method prints declaration of some method or constructor using given writer. <br>
     * Declaration includes list of parameters of method or constructor, list of exceptions,
     * that may me thrown by this method or constructor and an opening curly brace.
     * @param executable method or constructor, which declaration should be printed.
     * @throws IOException if Output error occurs, while writing.
     */
    private void printDeclaration(Executable executable) throws IOException {
        writer.write("(");
        printParameters(executable.getParameters());
        writer.write(")");
        printExceptions(executable.getExceptionTypes());
        writer.write(" {");
        writer.write(NEWLINE + DOUBLE_TAB);
    }

    /**
     * Method prints body of Impl class constructor. Constructor, that will be printed, is public.
     * The constructor consists of only one line, which is call of constructor of parent
     * class (super). <br>
     * If parent class is interface (so, it doesn't contain constructors),
     * the method won't be called, generated class will use default constructor, implicitly
     * created in the absence of other constructors.
     * @param constructor constructor to print.
     * @param className name of the class, to which the generated constructor belongs.
     * @throws IOException if an Output error occurs while writing.
     *
     * @see Implementor#printDeclaration(Executable)
     * @see Implementor#getArgumentString(int, IntFunction)
     * @see Writer
     * @see IOException
     */
    private void printConstructor(Constructor constructor,
                                          String className) throws IOException {
        writer.write(TAB + "public " + className);
        printDeclaration(constructor);
        writer.write("super(");
        String superArguments = getArgumentString(
                constructor.getParameterCount(),
                i -> "arg" + i
        );
        writer.write(superArguments + ");" + NEWLINE);
        writer.write(TAB + "}" + SKIP_LINE);
    }


    /**
     * Method is used to add abstract method from array of {@link Method} to {@link Set} of
     * {@link MethodWrapper}. Two methods are considered equal, if their signature (name and list of parameters)
     * are equal. <br>
     * Only abstract methods are added to set. <br>
     * {@link MethodWrapper} is used to provide the desired method equal for methods.
     * @param methods array of methods, that should be added to set.
     * @param set set of {@link MethodWrapper}, that stores methods with distinct signatures.
     *
     * @see Method
     * @see MethodWrapper
     * @see MethodWrapper#equals(Object)
     * @see Set
     * @see Method#getModifiers()
     * @see Modifier#isAbstract(int)
     */
    private void addMethodsToSet(Method[] methods, Set<MethodWrapper> set) {
        Arrays.stream(methods)
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .map(MethodWrapper::new)
                .forEach(set::add);
    }

    /**
     * Method is used to get {@link Set}, consists of {@link MethodWrapper}, wrapping abstract methods from
     * the {@link Implementor#token} superclasses. Signatures of methods, stored in set, are distinct. <br>
     * All methods in set are
     * <ul>
     *     <li>abstract</li>
     *     <li>declared in given class superclasses, not superinterfaces</li>
     * </ul>
     * {@link Implementor#addMethodsToSet(Method[], Set)} is used to add methods to set.
     * @return {@link Set} of {@link MethodWrapper}, wrapping abstract methods from given class superclasses,
     * with distinct signatures.
     *
     * @see Set
     * @see MethodWrapper
     * @see Method
     * @see Implementor#addMethodsToSet(Method[], Set)
     * @see Class#getSuperclass()
     */
    private Set<MethodWrapper> getAbstractMethodsFromSuperclasses() {
        Set<MethodWrapper> res = new HashSet<>();
        Class<?> cur = token;
        while (cur != null) {
            addMethodsToSet(cur.getDeclaredMethods(), res);
            cur = cur.getSuperclass();
        }
        return res;
    }

    /**
     * Prints implementation of given abstract method, using specified writer. <br>
     * Implementation consists of only one command, performing default value return. <br>
     * Implementation of method returns
     * <ul>
     *     <li>{@code false}, if method returns {@code boolean}</li>
     *     <li>{@code 0}, if method returns arithmetical primitive type ({@code int},
     *     {@code long}, {@code char}, {@code double}, {@code float}, {@code byte}, {@code short})</li>
     *     <li>nothing, if method returns <tt>void</tt></li>
     *     <li>{@code null}, otherwise</li>
     * </ul>
     * All of method's modifiers are printed before the implementation body, except
     * <ul>
     *     <li>{@code abstract}, because method with body cannot be abstract</li>
     *     <li>{@code transient}, because method cannot be transient</li>
     *     <li>{@code native}, because method implementation is written in Java, not in other languages</li>
     * </ul>
     * @param method abstract method to create implementation for.
     * @throws IOException if an Output error occurs while writing.
     *
     * @see Modifier
     * @see Method
     * @see Writer
     * @see IOException
     * @see Implementor#printDeclaration(Executable)
     */
    private void printMethod(Method method) throws IOException {
        Class resultType = method.getReturnType();
        String modifiers = Modifier.toString(
                method.getModifiers()
                        & ~Modifier.ABSTRACT
                        & ~Modifier.TRANSIENT
                        & ~Modifier.NATIVE
        );
        writer.write(TAB + modifiers + " " + resultType.getCanonicalName() + " " + method.getName());
        printDeclaration(method);
        writer.write("return");

        if (resultType.equals(void.class)) {
            writer.write(";");
        } else if (resultType.equals(boolean.class)) {
            writer.write(" false;");
        } else if (resultType.isPrimitive()) {
            writer.write(" 0;");
        } else {
            writer.write(" null;");
        }
        writer.write(NEWLINE + TAB + "}" + SKIP_LINE);
    }

    /**
     * Prints implementations of all abstract methods, declared in {@link Implementor#token} class,
     * it's superclasses and superinterfaces, using {@link Implementor#printMethod(Method)}. <br>
     * All implementations are printed one-by-one, using specified writer.
     * @throws IOException if an Output error occurs while writing.
     *
     * @see Writer
     * @see IOException
     * @see Class
     * @see Implementor#getAbstractMethodsFromSuperclasses()
     * @see Implementor#addMethodsToSet(Method[], Set)
     * @see Implementor#printMethod(Method)
     */
    private void printMethods() throws IOException {
        Set<MethodWrapper> methods = getAbstractMethodsFromSuperclasses();
        addMethodsToSet(token.getMethods(), methods);
        for (MethodWrapper methodWrapper : methods) {
            printMethod(methodWrapper.getMethod());
        }
    }

    /**
     * Gets class or interface to implement and generates source code for class, implementing it. <br>
     * Output file is created in subdirectory of the given {@code root}.
     * Output file is created in the folder, corresponding package of the given class, i.e. implementation of
     * {@link java.util.Collection} will be placed into <tt>root/java/util/CollectionImpl.java</tt>. <br>
     * The resulting class is located in a package, whose name coincides with the name of the package,
     * in which the given-user class is located. <br>
     * Output file contains public class, implementing given class or interface. This class has suffix Impl in classname.
     * Output file can be compiled without errors. <br>
     * Generated class contains implementations of all abstract method, declared in given class or interface, and in all
     * his superclasses or superinterfaces, and one public constructor, with which instances of Impl class can be
     * created. All methods of Impl class ignore their arguments and return default value:
     * <ul>
     *     <li>{@code false}, if method returns {@code boolean}</li>
     *     <li>{@code 0}, if method returns arithmetical primitive type ({@code int},
     *     {@code long}, {@code char}, {@code double}, {@code float}, {@code byte}, {@code short})</li>
     *     <li>nothing, if method returns <tt>void</tt></li>
     *     <li>{@code null}, otherwise</li>
     * </ul>
     *
     * @param token type token to create implementation for.
     * @param root root directory, where to place Impl.java file. If the directory doesn't exist, it will be created.
     * @throws info.kgeorgiy.java.advanced.implementor.ImplerException if implementation cannot be created because
     * <ul>
     *     <li>specified <tt>token</tt> is final</li>
     *     <li>specified <tt>token</tt> is enum</li>
     *     <li>specified <tt>token</tt> is primitive</li>
     *     <li>specified <tt>token</tt> is array</li>
     *     <li>specified <tt>token</tt> isn't interface and contains only private constructors</li>
     *     <li>Directory for .java file cannot be created</li>
     *     <li>If an input/output exception occurs</li>
     * </ul>
     *
     * @throws java.lang.NullPointerException if either <tt>token</tt> or <tt>root</tt> is null
     *
     * @see Implementor#checkCorrect()
     * @see info.kgeorgiy.java.advanced.implementor.Impler
     * @see info.kgeorgiy.java.advanced.implementor.ImplerException
     * @see java.lang.NullPointerException
     * @see Class
     * @see Impler#implement(Class, Path)
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        Objects.requireNonNull(token);
        Objects.requireNonNull(root);

        this.token = token;
        checkCorrect();
        Path pathToPackage = getPathToPackage(root);

        try {
            Files.createDirectories(pathToPackage);
        } catch (IOException e) {
            throw new ImplerException("Cannot create directory for .java file " + e.getMessage());
        }

        className = token.getSimpleName() + "Impl";
        Path pathToFile = pathToPackage.resolve(className + ".java");

        try (Writer writer = new UnicodeWriter(Files.newBufferedWriter(pathToFile))) {
            //TODO use java 9 for creating this.writer inside try()
            this.writer = writer;

            printPackage();
            writer.write("public class " + className + " ");
            if (token.isInterface()) {
                writer.write("implements ");
            } else {
                writer.write("extends ");
            }
            writer.write(token.getName() + " {" + NEWLINE);
            if (nonPrivateConstructor != null) {
                assert (token.isInterface());
                printConstructor(nonPrivateConstructor, className);
            }
            printMethods();
            writer.write("}");
        } catch (IOException e) {
            throw new ImplerException("Error while writing to output file: " + e.getMessage());
        }
    }

    /**
     * Creates <tt>.class</tt> file with implementation of given class. <br>
     * First, generates <tt>.java</tt> file with source code with implementation of given class, using
     * {@link Impler#implement(Class, Path)}. Source code is put in <tt>pathToTempDirectory</tt>. <br>
     * After creating source code, compiles it, using default Java compiler, returned by
     * {@link ToolProvider#getSystemJavaCompiler()}. Compiled class is put in temp directory, with source code. <br>
     * Generated class package name is similar to given class package name.
     * @param token class to create implementation for.
     * @param pathToTempDirectory path to temp directory, where source code and compiled .class file with Impl class
     *                            will be located.
     * @return path to compiled .class file with implementation of <tt>token</tt> class. The .class file is located
     * in <tt>pathToTempDirectory</tt>.
     * @throws ImplerException if
     * <ul>
     *     <li>given class cannot be extended</li>
     *     <li>Java compiler cannot be found</li>
     *     <li>an input/output exception occurs</li>
     *     <li>directories for temporary files cannot be created</li>
     *     <li>compiler returns non-zero result code</li>
     * </ul>
     *
     * @since HW 5
     *
     * @see JavaCompiler
     * @see ToolProvider#getSystemJavaCompiler()
     * @see Implementor#implement(Class, Path)
     */
    private Path implementAndCompile(Class<?> token, Path pathToTempDirectory) throws ImplerException {
        implement(token, pathToTempDirectory);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ImplerException("Couldn't compile class: compiler not found");
        }
        int compileResult = compiler.run(null, null, null,
                getPathToPackage(pathToTempDirectory).resolve(className + ".java").toString(),
                "-cp",
                pathToTempDirectory.toString() + File.pathSeparator
                        + System.getProperty("java.class.path")
        );
        if (compileResult != 0) {
            throw new ImplerException("Cannot compile class, compiler returned " + compileResult);
        }
        return getPathToPackage(pathToTempDirectory).resolve(className + ".class");
    }

    /**
     * Creates <tt>.jar</tt>-file, containing specified by third argument <tt>.class</tt>-file. Path to <tt>.jar</tt>-file
     * is specified by second argument.
     * @param token class, that is implemented by <tt>.class</tt>-file, that will be put in <tt>.jar</tt>-file.
     * @param jarFile path, where <tt>.jar</tt>-file should be created.
     * @param pathToClassFile path to <tt>.class</tt> file, that should be put in <tt>.jar</tt>-archive.
     * @throws IOException if an Input/Output occurs while reading from <tt>.class</tt>-file or while writing to
     * <tt>.jar</tt>-archive.
     *
     * @since HW 5
     *
     * @see JarOutputStream
     * @see Manifest
     * @see ZipEntry
     * @see Path
     */
    private void createJarFile(Class<?> token, Path jarFile, Path pathToClassFile) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            String fullClassName = token.getCanonicalName().replace(".", "/") + "Impl.class";
            out.putNextEntry(new ZipEntry(fullClassName));
            Files.copy(pathToClassFile, out);
            out.closeEntry();
        }
    }

    /**
     * Method gets class or interface to implement and generates <tt>.jar</tt>-archive, containing class,
     * implementing it. <br>
     * Name and location of generated <tt>.jar</tt>-file if specified by second argument of the method.<br>
     * The resulting class is located in a package, whose name coincides with the name of the package,
     * in which the given-user class is located. <br>
     * Output <tt>.jar</tt>-file contains compiled public class, implementing given class or interface.
     * This class has suffix Impl in classname.
     * Generated class contains implementations of all abstract method, declared in given class or interface, and in all
     * his superclasses or superinterfaces, and one public constructor, with which instances of Impl class can be
     * created. All methods of Impl class ignore their arguments and return default value:
     * <ul>
     *     <li>{@code false}, if method returns {@code boolean}</li>
     *     <li>{@code 0}, if method returns arithmetical primitive type ({@code int},
     *     {@code long}, {@code char}, {@code double}, {@code float}, {@code byte}, {@code short})</li>
     *     <li>nothing, if method returns <tt>void</tt></li>
     *     <li>{@code null}, otherwise</li>
     * </ul>
     * {@link Implementor#implement(Class, Path)} is used for generating source code of Impl class.<br>
     * {@link Implementor#createJarFile(Class, Path, Path)} is used for creating <tt>.jar</tt>-archive. <br>
     * Method creates folder for temporary files in the directory, where <tt>.jar</tt>-file should be placed.
     * Temporary directory is created using {@link Files#createTempDirectory(Path, String, FileAttribute[])}, so
     * directory is guaranteed to have unique full name.<br>
     * After generating <tt>.jar</tt>-file is finished, method tries to delete directory for temporary files. If
     * deleting ends successfully, method doesn't do anything. If directory cannot be deleted, method reports
     * about it using {@link System#err}.
     * @param token type token to create implementation for.
     * @param jarFile target <tt>.jar</tt> file.
     * @throws ImplerException f implementation cannot be created because
     * <ul>
     *     <li>specified <tt>token</tt> is final</li>
     *     <li>specified <tt>token</tt> is enum</li>
     *     <li>specified <tt>token</tt> is primitive</li>
     *     <li>specified <tt>token</tt> is array</li>
     *     <li>specified <tt>token</tt> isn't interface and contains only private constructors</li>
     *     <li>Either directory for <tt>.jar</tt>-file or <tt>.jar</tt>-file itself cannot be created</li>
     *     <li>If an input/output exception occurs</li>
     *     <li>If Java compiler cannot be found</li>
     *     <li>If class compilation ends with non-zero code</li>
     * </ul>
     *
     * @throws java.lang.NullPointerException if either <tt>token</tt> or <tt>jarFile</tt> is null
     *
     * @since HW 5
     *
     * @see Implementor#implement(Class, Path)
     * @see Class
     * @see ImplerException
     * @see NullPointerException
     * @see Implementor#createJarFile(Class, Path, Path)
     * @see JarImpler#implementJar(Class, Path)
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        Objects.requireNonNull(token);
        Objects.requireNonNull(jarFile);

        this.token = token;
        Path temporaryDir;
        try {
            temporaryDir = Files.createTempDirectory(jarFile.toAbsolutePath().getParent(), "tmp");
        } catch (IOException e) {
            throw new ImplerException("Directory for temporary files cannot be created " + e.getMessage());
        }

        try {
            Path pathToClassFile = implementAndCompile(token, temporaryDir);
            createJarFile(token, jarFile, pathToClassFile);
        } catch (IOException e) {
            throw new ImplerException("Cannot create jar file with implementation: " + e.getMessage());
        } finally {
            try {
                Files.walkFileTree(temporaryDir, TEMP_DIRECTORY_CLEANER);
            } catch (IOException e) {
                System.err.println("Couldn't clean directory for temporary files " + temporaryDir);
            }
        }
    }
}
