"""Writes a minimal empty game test structure (1x1x1 air) as gzipped NBT."""
import gzip
import struct


def tag_string(s):
    b = s.encode()
    return struct.pack(">H", len(b)) + b


def payload(tag_type, value):
    if tag_type == 3:  # int
        return struct.pack(">i", value)
    if tag_type == 8:  # string
        return tag_string(value)
    if tag_type == 9:  # list
        item_type, items = value
        return bytes([item_type]) + struct.pack(">i", len(items)) + b"".join(items)
    if tag_type == 10:  # compound
        return b"".join(named(t, n, v) for n, t, v in value) + b"\x00"
    if tag_type == 4:  # long
        return struct.pack(">q", value)
    raise ValueError(tag_type)


def named(tag_type, name, value):
    return bytes([tag_type]) + tag_string(name) + payload(tag_type, value)


structure = [
    ("DataVersion", 3, 3955),
    ("size", 9, (3, [struct.pack(">i", 3)] * 3)),
    ("palette", 9, (10, [
        b"".join([
            named(8, "Name", "minecraft:air"),
        ])
    ])),
    ("blocks", 9, (10, [])),
    ("entities", 9, (10, [])),
]

root = bytes([10]) + tag_string("") + payload(10, structure) + b"\x00"
with gzip.open("src/main/resources/data/eldritch/structure/empty.nbt", "wb") as f:
    f.write(root)
print("wrote empty.nbt")
