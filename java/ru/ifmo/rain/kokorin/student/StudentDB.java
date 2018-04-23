package ru.ifmo.rain.kokorin.student;

import info.kgeorgiy.java.advanced.student.Group;
import info.kgeorgiy.java.advanced.student.Student;
import info.kgeorgiy.java.advanced.student.StudentGroupQuery;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Ilya Kokorin
 */
public class StudentDB implements StudentGroupQuery {

    private static final Comparator<Student> NAME_COMPARATOR =
            Comparator.comparing(Student::getLastName)
                    .thenComparing(Student::getFirstName)
                    .thenComparingInt(Student::getId);

    private static String getFullName(Student student) {
        return student.getFirstName() + " " + student.getLastName();
    }

    private static<T, R> List<R> mapAndToList(Stream<T> stream,
                                              Function<T, R> mapper) {
        return stream
                .map(mapper)
                .collect(Collectors.toList());
    }

    private static List<String> mapStudents(List<Student> students, Function<Student, String> mapper) {
        return mapAndToList(students.stream(), mapper);
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return mapStudents(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return mapStudents(students, Student::getLastName);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return mapStudents(students, StudentDB::getFullName);
    }

    @Override
    public List<String> getGroups(List<Student> students) {
        return mapStudents(students, Student::getGroup);
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return new TreeSet<>(getFirstNames(students));
    }

    @Override
    public String getMinStudentFirstName(List<Student> students) {
        return students.stream()
                .min(Comparator.comparingInt(Student::getId))
                .map(Student::getFirstName)
                .orElse("");
    }

    private List<Student> filterAndSort(Collection<Student> students,
                                        Predicate<Student> predicate,
                                        Comparator<Student> comparator) {
        return students.stream()
                .filter(predicate)
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private List<Student> filterAndSort(Collection<Student> students,
                                        Predicate<Student> predicate) {
        return filterAndSort(students, predicate, NAME_COMPARATOR);
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return filterAndSort(students,
                student -> true,
                Comparator.comparingInt(Student::getId)
        );
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return filterAndSort(students,
                student -> true
        );
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return filterAndSort(students,
                student -> student.getFirstName().equals(name)
        );
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return filterAndSort(students,
                student -> student.getLastName().equals(name)
        );
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, String group) {
        return filterAndSort(students,
                student -> student.getGroup().equals(group)
        );
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, String group) {
        return students.stream()
                .filter(student -> student.getGroup().equals(group))
                .collect(Collectors.toMap(
                        Student::getLastName,
                        Student::getFirstName,
                        BinaryOperator.minBy(Comparator.naturalOrder())
                ));
    }

    private static Stream<Entry<String, List<Student>>> getEntryStream(Collection<Student> students) {
        return students.stream()
                .collect(Collectors.groupingBy(
                        Student::getGroup,
                        TreeMap::new,
                        Collectors.toList()
                )).entrySet()
                .stream();
    }

    private List<Group> getGroupsBy(Collection<Student> students,
                                    Function<Collection<Student>, List<Student>> mapper) {
        return mapAndToList(getEntryStream(students),
                entry -> new Group(entry.getKey(), mapper.apply(entry.getValue())));
    }

    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return getGroupsBy(students, this::sortStudentsByName);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return getGroupsBy(students, this::sortStudentsById);
    }

    private static String getMax(Stream<Entry<String, List<Student>>> stream,
                                 ToIntFunction<List<Student>> listToIntMapper) {
        return stream
                .max(
                        Comparator.comparingInt(
                                (Map.Entry<String, List<Student>> entry) -> listToIntMapper.applyAsInt(entry.getValue())
                        ).thenComparing(Map.Entry::getKey, Collections.reverseOrder(String::compareTo))
                ).map(Entry::getKey)
                .orElse("");

    }

    @Override
    public String getLargestGroup(Collection<Student> students) {
        return getMax(getEntryStream(students),
                List::size
        );
    }

    @Override
    public String getLargestGroupFirstName(Collection<Student> students) {
        return getMax(getEntryStream(students),
                list -> getDistinctFirstNames(list).size()
        );
    }
}
/*
D:\java-advanced-2018\out\production\java-advanced-2018>java -cp ".;D:/java-advanced-2018/artifacts/StudentTest.jar;D:/java-advanced-2018/lib/*" info.kgeorgiy.java.advanced.student.Tester StudentGroupQuery ru.ifmo.rain.kokorin.student.StudentDB
*/