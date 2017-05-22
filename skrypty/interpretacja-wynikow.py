def gogo (text):
	from math import log2
	rez = [[None]*6 for i in range(12)]
	tasks = None
	pernode = None
	for line in text.splitlines ():
		if "Tasks" in line:
			tasks = int(log2(float(line.split()[1])))
			pernode= int(log2(float(line.split()[7])))
		if "performance" in line and "skipped" not in line:
			perf=float(line.split()[11])
			print(tasks, pernode, perf)
			if rez[tasks][pernode] is None or rez[tasks][pernode] < perf: rez[tasks][pernode] = perf
	return rez


def interpret (arr):
	for x in range(len(arr)):
		for y in range(len(arr[x])):
			elem = arr[x][y]
			if elem is None: elem = 0
			print (elem, end='\t')
		print ()


def read_all_lines() -> str:
	contents = []
	while True:
		try:
			line = input()
		except EOFError:
			break
		contents.append(line)
	return "\n".join(contents)

dane="""

"""
if __name__ == "__main__":
	wynik = gogo(dane)
	interpret(wynik)