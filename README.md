# BRTracer with PyTracer

This is the class project for SWE 215 at UCI
# Contributions

BRTracer/: re-implementation of the original BRTracer paper with modification that works for any projects.
PyTracer/: our new modification that takes context of tickets and source codes into the account.
Dataset/: contains the three projects mentioned from the original BRTracer paper and a langj project that we build based on the defects4j projects.

Re-implement the BrTracer paper with several modifications.
1. Make it work with any project instead of hard-coded with only the three projects mentioned in the paper.
2. Implement an add-on called PyTracer that takes context embeddings into account using deep learning models and adopts the segmentation mentioned in the original paper (method level).
3. Combine the results of both original BRTracer and PyTracer.

Usage: First run PyTracer and then run BRTracer.
