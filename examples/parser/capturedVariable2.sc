Include("examples/assert.sc")

val x = (
  val x = 5 in
  def f(x: Nat): Nat = { 2 * x }
  val x = f(x) in
  x - x
) in
assert((x == 0))
