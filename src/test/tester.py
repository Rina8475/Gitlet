"""
This file is used to test gitlet.
Flags:
    -c: clear the working directory after the test.
"""

import sys
import os
import subprocess
import argparse
import re

from shutil import copyfile
from contextlib import contextmanager


class TestException(Exception):
    pass

####################
# Tester Operations
####################
"""
Test-file format:
< <command>
<expected output>
>>>

< <command>
<expected output> (regex)
>>>*
"""
# symbol table
SYMBOLS = {}
# command prefix
COMMAND_PREFIX = "Execute: "

def replace_token(token): 
    """
    >>> SYMBOLS["world"] = "world"
    >>> replace_token("hello $world")
    'hello $world'
    >>> replace_token("hello ${world}")
    'hello world'
    >>> replace_token("hello ${world} ${world}")
    'hello world world'
    """
    start_idx = token.find("$")
    if start_idx == -1 or token[start_idx + 1] != "{":
        return token
    end_idx = token[start_idx:].find("}")
    if end_idx == -1:
        return token
    var_name = token[start_idx+2:start_idx+end_idx]
    if not is_valid_name(var_name) or var_name not in SYMBOLS:
        return token
    return token[:start_idx] + SYMBOLS[var_name] + replace_token(token[start_idx + end_idx + 1:])

def read_from_file(file_path):
    with open(file_path, "r") as f:
        for line in f:
            yield replace_token(line)

def read_tails(reader):
    buffer = ""
    for line in reader:
        if line.startswith(">>>*"):
            return ">>>*", buffer
        elif line.startswith(">>>+"):
            return ">>>+", buffer
        elif line.startswith(">>>"):
            return ">>>", buffer
        else:
            buffer += line

def read_tokens(reader):
    for line in reader:
        if line.startswith("<"):
            yield "<", (line[1:].strip(), *read_tails(reader))
        if line.startswith("EF"):
            yield "EF", line[2:].strip()
        if line.startswith("ED"):
            yield "ED", line[2:].strip()
        if line.startswith("D"):
            yield "D", line[1:].strip()
        if line.startswith("NEF"):
            yield "NEF", line[3:].strip()
        if line.startswith("#") or line.strip() == "":
            continue

def do_execute(expression, env):
    cmd, regex_flag, expected = expression
    print(COMMAND_PREFIX + cmd)
    out, err, code = execute_cmd(cmd)

    if regex_flag == ">>>+":
        if out:
            raise TestException(f"Output should be empty: \n{out}")
        if code == 0:
            raise TestException(f"Command should not fail with code {code}")
        if err != expected:
            raise TestException(f"Unexpected error: \n{compare_strings_icdiff(expected, err)}")
        return None
    if err:
        raise TestException(f"error: \n{out}\n" + "****************************\n" \
                            + f"{err}\n")
    if regex_flag == ">>>*":
        match = re.match(expected, out)
        if not match:
            raise TestException(f"Output does not match regex: \n>>> (expected)\n{expected}" \
                                + f"<<< (actual)\n{out}")
        table = {}
        lastindex = match.lastindex if match.lastindex is not None else 0
        for i in range(1, lastindex+1):
            table[str(i)] = match.group(i)
        return table
    if regex_flag == ">>>":
        if out != expected:
            raise TestException(f"Unexpected output: \n{compare_strings_icdiff(expected, out)}")
        return None
    raise TestException(f"Invalid regex flag: {regex_flag}")

def is_valid_name(symbol):
    return re.match(r"^[a-zA-Z0-9_]+$", symbol)

def is_regex_variable(symbol):
    """
    >>> is_regex_variable("${10}")
    True
    >>> is_regex_variable("${var}")
    False
    >>> is_regex_variable("$var")
    False
    """
    return re.match(r"^\$\{([0-9]+)\}$", symbol) is not None

def get_regex_variable_name(symbol):
    """
    >>> get_regex_variable_name("${10}")
    '10'
    """
    return symbol[2:-1]

def do_define(expression, env):
    name, value = expression.split("=")
    name, value = name.strip(), value.strip()
    if not is_valid_name(name):
        raise TestException(f"Invalid name: {name}")
    if is_regex_variable(value):
        SYMBOLS[name] = env[get_regex_variable_name(value)]
    else: 
        SYMBOLS[name] = value

def do_assert_file_exist(expression, env):
    for file_path in expression.split():
        if not is_file_exist(file_path):
            raise TestException(f"File not found: {file_path}")
        
def do_assert_dir_exist(expression, env):
    for dir_path in expression.split():
        if not is_dir_exist(dir_path):
            raise TestException(f"Directory not found: {dir_path}")

def do_assert_not_file_exist(expression, env):
    for file_path in expression.split():
        if is_file_exist(file_path):
            raise TestException(f"File exists: {file_path}")

SPECIAL_FORMS = {
    "<": do_execute,
    "D": do_define,
    "EF": do_assert_file_exist,
    "ED": do_assert_dir_exist,
    "NEF": do_assert_not_file_exist,
}

def execute_token(pre, expression, env):
    return SPECIAL_FORMS[pre](expression, env)

def execute_cmd(cmd):
    p = subprocess.Popen(cmd, shell=True, executable="/bin/zsh", stdout=subprocess.PIPE, \
                        stderr=subprocess.PIPE)
    stdout, stderr = p.communicate()
    return stdout.decode("utf-8"), stderr.decode("utf-8"), p.returncode

def compare_strings_icdiff(expected, actual):
    """Compare two strings and return the ndiff result"""
    os.mkdir("temp")
    with open("temp/expected", "w") as f:
        f.write(expected)
    with open("temp/actual", "w") as f:
        f.write(actual)
    out, _, __ = execute_cmd("cd temp && icdiff expected actual")
    os.remove("temp/expected")
    os.remove("temp/actual")
    os.rmdir("temp")
    return out

def get_var_name(symbol):
    """
    >>> get_var_name("${var}")
    'var'
    """
    match = re.match(r"^\$\{([a-zA-Z0-9_]+)\}$", symbol)
    return match.group(1)

#####################
# File Operations   #
#####################
JAR_PATH = "../../gitlet.jar"
OTHER_FILES = tuple(map(lambda x: os.path.join("src", x), os.listdir("src")))

def is_file_exist(file_path):
    return os.path.exists(file_path) and os.path.isfile(file_path)

def is_dir_exist(dir_path):
    return os.path.exists(dir_path) and os.path.isdir(dir_path)

def copy_file(file, src, dst):
    copyfile(os.path.join(src, file), os.path.join(dst, file))

def del_dir(dir_path):
    for root, dirs, files in os.walk(dir_path, topdown=False):
        for name in files:
            os.remove(os.path.join(root, name))
        for name in dirs:
            del_dir(os.path.join(root, name))
    os.rmdir(dir_path)

@contextmanager
def change_wkd(dir):
    """Change the working directory to the given directory
    Change back to the previous directory if call this function again."""
    assert is_dir_exist(dir), f"Directory not found: {dir}"
    prev_dir = os.getcwd()
    try:
        os.chdir(dir)
        yield
    finally:
        os.chdir(prev_dir)

def set_wkd(wkd: str, *paths: str):
    """Setting for working directory, Assuming the directory is not existed.
    Create the directory and copy the needed files to the directory."""
    assert not os.path.exists(wkd), f"Directory already exists: {wkd}"
    os.makedirs(wkd)
    for path in paths:
        src_dir, file = os.path.split(path)
        copy_file(file, src_dir, wkd)

def get_wkd(file_path: str):
    """Generate the working directory for a given test file"""
    return os.path.split(file_path)[1].split(".")[0]

def do_test(test_file):
    """do the test for a single file"""
    env = None
    reader = read_from_file(test_file)
    for pre, token in read_tokens(reader):
        env = execute_token(pre, token, env)
    print(f"Test {test_file} passed.")

# main
parser = argparse.ArgumentParser()
parser.add_argument("file", type=str, nargs="+")
parser.add_argument("-c", "--clear", action="store_true")
args = parser.parse_args()

test_files = [f for f in args.file if f.endswith(".in")]
assert all(is_file_exist(f) for f in test_files), "Input file not found"

for test_file in test_files:
    test_name = get_wkd(test_file)
    set_wkd(test_name, test_file, JAR_PATH, *OTHER_FILES)
    try:
        with change_wkd(test_name):
            do_test(test_name + ".in")
    except TestException as e:
        print(e, file=sys.stderr)
        print(f"Test {test_name} failed")
    finally:
        if args.clear:
            del_dir(test_name)
