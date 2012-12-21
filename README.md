# SBT Test Reports

This is an SBT plugin that produces reports for tests.

This was only tested with an sbt junit framework, but should work with any sbt test framework.

## Usage

Add `addSbtPlugin("com.dadrox" %% "sbt-test-reports" % "0.1")` to `project/plugins.sbt`.

Add `testListeners <+= target map (t => new com.dadrox.sbt.test.reports.Xml(t getName))` to your full SBT configuration.

## Compatibility

Should work with the following scala versions:

 * 2.9.x
 * 2.10.x
 
Should work with the following SBT versions:

 * 0.11.x
 * 0.12.x

## Known Issues

 * Only works for full configurations.
 * Only XML is supported.
 * Does not include stdout/err in the reports for failures and errors.

## License

Copyright (C) 2012-2013, Christopher Wood (dadrox)

Published under [BSD 2-Clause License](http://opensource.org/licenses/BSD-2-Clause)