#
# perf-xml-to-csv.py
#
# Collect data in XML performance reports as a CSV file.
#

import os


def get_report_for_fileset(fileset):
    for filename in fileset:
        print("Processing %s" % filename)
        yield get_results(filename)


def get_results(filename):
    import xml.etree.ElementTree as ET
    tree = ET.parse(filename)
    print(tree.getroot())
    config = tree.find("config")
    properties = config.findall("property")
    props = {}
    for prop in properties:
        props[prop.attrib["key"]] = prop.attrib["value"]

    report = tree.find("report")
    timings = report.findall("time")

    times = {}
    for time in timings:
        times[time.attrib["id"]] = time.attrib["t"]

    commits = report.find("commits")
    other = {"n": commits.attrib["n"]}

    row = {}
    row.update(props)
    row.update(times)
    row.update(other)
    return row


def generate_csv(generator):
    import csv
    writer = None
    with open('performance_results.csv', 'w') as csvfile:
        for row in generator:
            if not writer:
                fieldnames = list(row.keys())
                writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
                writer.writeheader()
            writer.writerow(row)


# TODO: Make sure to put argparse and remove hard-coding.
# This is just proof of concept right now.
fileset = [dir for dir in os.listdir(".") if dir.startswith("astropy-perf")]
print(fileset)

row_gen = get_report_for_fileset(fileset)
generate_csv(row_gen)
