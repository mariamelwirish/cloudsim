from collections import Counter

# Read the falls.txt file and extract the second column
second_column_values = []
last_first_value = None

with open('falls.txt', 'r') as f:
    for line in f:
        line = line.strip()
        if line:  # Skip empty lines
            parts = line.split(',')
            if len(parts) >= 2:
                second_column_values.append(parts[1])
                last_first_value = parts[0]

# Count occurrences of each value
value_counts = Counter(second_column_values)

# Sort by value (numerically) for better readability
sorted_counts = sorted(value_counts.items(), key=lambda x: int(x[0]))

# Display the results
print("Number of falls to LR for #VMs > 50")
print("-" * 40)
print(f"{'#VMs':<10} {'#Falls':<10}")
print("-" * 40)

for value, count in sorted_counts:
    print(f"{value:<10} {count:<10}")

print("-" * 40)
total_unique_values = len(value_counts)


# Monte Carlo computation based on the first number of the last row
try:
    monte_carlo_iters = int(last_first_value) if last_first_value is not None else 0
except ValueError:
    monte_carlo_iters = 0

product = monte_carlo_iters * total_unique_values

print(f"{'Total unique values:'} {total_unique_values}")
print(f"{'Total falls:'} {len(second_column_values)} {' / '} {product}")
print(f"{'Monte Carlo iters:'} {monte_carlo_iters}")
print(f"{'Total Iterations (for #VMs > 50):'} {product}")
