import os


def get_report_for_fileset(fileset):
    for filename in fileset:
        print("Processing %s" % filename)
        row_gen = get_report_rows(filename)
        for row in row_gen:
            yield(row)

def get_report_rows(filename):
    import xml.etree.ElementTree as ET
    tree = ET.parse('astropy-cloc-n16-c12-0-10.xml')
    print(tree.getroot())
    cloc_phase_list = tree.findall("cloc_phase")
    print(len(cloc_phase_list))
    for cloc_phase in cloc_phase_list:
        common_info = {
            'order': cloc_phase.find("order").text.strip(),
            'commit': cloc_phase.find("commit").text.strip(),
            'path':  cloc_phase.find("path").text.strip()
        }
        print("Processing commit: %s" % cloc_phase.find("commit").text.strip())
        languages = cloc_phase.findall("report/cloc/languages/language")
        for language in languages:
            row = common_info.copy()
            row.update(language.attrib)
            yield(row)


def generate_csv(generator):
    import csv
    writer = None
    with open('experimental_results.csv', 'w') as csvfile:
        for row in generator:
            if not writer:
                fieldnames = list(row.keys())
                writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
                writer.writeheader()
            writer.writerow(row)


# TODO: Make sure to put argparse and remove hard-coding.
# This is just proof of concept right now.
fileset = [dir for dir in os.listdir(
    ".") if dir.startswith("astropy-cloc-n16-c12")]
print(fileset)

row_gen = get_report_for_fileset(fileset)
generate_csv(row_gen)
