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

SYMBOLS = {}

class TestException(Exception):
    pass

def read_from_file(file_path):
    with open(file_path, "r") as f:
        for line in f:
            yield line

def read_tokens(reader):
    status = False
    buffer = ""
    for line in reader:
        if line.startswith("<"):
            yield "<", line[1:].strip()
            status = True
        elif status:
            if line.startswith(">>>*"):
                yield ">>>*", buffer
                buffer = ""
                status = False
            elif line.startswith(">>>"):
                yield ">>>", buffer
                buffer = ""
                status = False
            else:
                buffer += line
        if line.startswith("EF"):
            yield "EF", line[2:].strip()
        if line.startswith("ED"):
            yield "ED", line[2:].strip()
        if line.startswith("D"):
            yield "D", line[1:].strip()
        if line.startswith("NEF"):
            yield "NEF", line[3:].strip()
        if line.startswith("#"):
            continue

def run_command(command):
    print(f"Execute: {command}")
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = p.communicate()
    return stdout.decode("utf-8"), stderr.decode("utf-8")

def execute_token(pre, token, remain):
    if pre == "<":
        out, err = run_command(token)
        if err:
            print(f"Error: {err}")
            raise TestException()
        return out
    elif pre == ">>>":
        assert type(remain) == str, "remain should be a string"
        if remain != token:
            print(f"Unexpected output: \n{compare_strings_icdiff(token, remain)}")
            raise TestException()
    elif pre == ">>>*":
        match = re.match(token, remain)
        if not match:
            print(f"Unexpected output: \n>>> (expected)\n{token}<<< (actual)\n{remain}")
            raise TestException()
        else:
            return match
    elif pre == "D":
        name, value = token.split("=")
        name, value = name.strip(), value.strip()
        if is_valid_name(name) and is_valid_variable(value):
            group_id = int(get_var_name(value))
            if group_id > remain.lastindex:
                print(f"Invalid var name: ${'{' + str(group_id) + '}'}", file=sys.stderr)
                raise TestException()
            SYMBOLS[name] = remain.group(group_id)
        else:
            print(f"Invalid expression: {token}", file=sys.stderr)
            raise TestException()
    elif pre == "EF":
        for name in token.split():
            if not is_file_exist(name):
                print(f"File not found: {name}", file=sys.stderr)
                raise TestException()
    elif pre == "ED":
        for name in token.split():
            if not is_dir_exist(name):
                print(f"Directory not found: {name}", file=sys.stderr)
                print(f"Current directory: {os.getcwd()}", file=sys.stderr)
                raise TestException()
    elif pre == "NEF":
        for name in token.split():
            if is_file_exist(name):
                print(f"File exists: {name}", file=sys.stderr)
                raise TestException()
    return ""    # default return value

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

def compare_strings_icdiff(expected, actual):
    """Compare two strings and return the ndiff result"""
    os.mkdir("temp")
    with open("temp/expected", "w") as f:
        f.write(expected)
    with open("temp/actual", "w") as f:
        f.write(actual)
    out, err = run_command("cd temp && icdiff expected actual")
    os.remove("temp/expected")
    os.remove("temp/actual")
    os.rmdir("temp")
    return out

def is_valid_name(symbol):
    return re.match(r"^[a-zA-Z0-9_]+$", symbol)

def is_valid_variable(symbol):
    """
    >>> is_valid_variable("${var}")
    True
    >>> is_valid_variable("$var")
    False
    """
    return re.match(r"^\$\{([a-zA-Z0-9_]+)\}$", symbol) is not None

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
    remain = ""
    reader = read_from_file(test_file)
    for pre, token in read_tokens(reader):
        token = replace_token(token)
        remain = execute_token(pre, token, remain)
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
