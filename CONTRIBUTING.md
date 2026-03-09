# Contributing to zMAnnounce

Thank you for your interest in contributing! Please follow these guidelines to help keep the project organized and high quality.

## Code of Conduct
- Be respectful and inclusive
- Provide constructive feedback
- Accept responsibility for mistakes
- Show empathy

## How to Contribute

### Reporting Bugs
1. Check [issues](https://github.com/zMarkitos/zMAnnounce/issues)
2. If not reported, open a new issue with:
   - Steps to reproduce
   - Expected vs actual behavior
   - Server, Java, plugin version
   - Relevant logs

### Suggesting Features
1. Check existing issues
2. Open a new issue with `enhancement` label
3. Describe clearly and explain benefits

### Contributing Code
1. Fork repository
2. `git checkout -b feature/your-feature`
3. Make changes following coding standards
5. `mvn clean package` and ensure passing
6. Commit & push
7. Open Pull Request

## Development Setup
- Java 21+
- Maven 3.6+
- Git

Build:
```bash
git clone https://github.com/zMarkitos/zMAnnounce.git
cd zMAnnounce
mvn mvn clean package