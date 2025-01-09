
import sys
import xml.etree.ElementTree

def parse_junit_xml(file_path):
    tree = xml.etree.ElementTree.parse(file_path)
    root = tree.getroot()

    # Assuming the root is 'testsuite' or 'testsuites'
    if root.tag == 'testsuite':
        parse_testsuite(root)
    elif root.tag == 'testsuites':
        for testsuite in root:
            parse_testsuite(testsuite)

def parse_testsuite(testsuite):
    for testcase in testsuite.findall('testcase'):
        test_class_name = testcase.get('classname')
        test_class_duration_milliseconds = int(float(testsuite.get('time'))*1000)
        test_name = testcase.get('name')
        test_duration_milliseconds = int(float(testcase.get('time'))*1000)
        print(f'{{"test_class_name": "{test_class_name}", "test_class_duration_milliseconds": {test_class_duration_milliseconds}, "test_name": "{test_name}", "test_status": "flaky", "test_duration_milliseconds": {test_duration_milliseconds}}}')

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 testdetail.py path/to/TEST-name-FLAKY.xml")
        sys.exit(1)

    parse_junit_xml(sys.argv[1])
