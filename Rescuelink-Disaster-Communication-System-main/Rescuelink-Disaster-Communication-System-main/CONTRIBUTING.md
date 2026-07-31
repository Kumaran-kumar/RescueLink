# Contributing to RescueLink

First off, thank you for considering contributing to RescueLink! It's people like you that make open source tools for disaster scenarios reliable and effective.

## How Can I Contribute?

### Reporting Bugs
This section guides you through submitting a bug report for RescueLink.
*   **Ensure the bug was not already reported** by searching on GitHub under Issues.
*   If you're unable to find an open issue addressing the problem, open a new one. Be sure to include a title and clear description, as much relevant information as possible, and a code sample or an executable test case demonstrating the expected behavior that is not occurring.

### Suggesting Enhancements
*   Open a new issue with the label `enhancement`.
*   Provide a clear and descriptive title.
*   Explain why this enhancement would be useful to most users.

### Pull Requests
1.  Fork the repo and create your branch from `main`.
2.  If you've added code that should be tested, add tests.
3.  Ensure your code adheres to standard Java/Android style guidelines.
4.  Issue that pull request!

## Code Style
*   Follow standard Java naming conventions (CamelCase for classes, camelBack for variables/methods).
*   Use meaningful variable names.
*   Comment your code, especially in complex mesh networking logic.

## Developing the Mesh Network
Testing the Google Nearby Connections API requires at least two physical Android devices. Emulators often fail to simulate Bluetooth and Wi-Fi Direct hardware correctly, leading to false negatives during testing.
