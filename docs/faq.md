# Frequently Asked Questions

## What about hx?

[hx](https://github.com/Lokeh/hx) was the library which helix originated from.
hx was created with similar goals and ideas, but continued use and
experimentation led to the desire to remove or change several portions of the
library which would break the few people that had adopted it.

Rather than do a breaking change, a new name was created with the updated API.
Notable changes was the removal of hx's hiccup interpreter, using kebab-case for
all APIs, and a new focus on compile-time optimizations and linting.

## What about hiccup?

Hiccup is great. Hiccup, however, comes at a cost; it is very difficult to
statically convert hiccup to React Elements at compile time. At run time,
creating vectors that are then parsed and turned into React Elements is a
slight, but constant, performance tax on your application and makes me feel bad
about warming the planet up just to save a few keystrokes.

Popular libraries like Reagent, which parse hiccup at runtime, ameliorates this
performance cost by trying to give the developer tight control over when and
where state updates trigger renders; a single state update, if it is dependend
on by 3 leaf nodes, will only trigger a render of those individual leafs. React
state, passed down by a parent, will instead trigger a render of the parent and
the entire tree will re-render. This means that using React's local component
state and passing down props feels the performance problems posed by hiccup much
greater.

However, Reagents methods of selectively rendering subscribed components comes
with tradeoffs, especially when trying to use Concurrent Mode. 
[Subscriptions to external state](https://stackoverflow.com/questions/58611408/what-kinds-of-state-updates-can-we-defer-with-usetransition)
are a big part of the conversation when talking about migrating to Concurrent
Mode.

["Tearing"](https://stackoverflow.com/questions/54891675/what-is-tearing-in-the-context-of-the-react-redux)
is a common term used when a Concurrent Mode render cycle pauses, then resumes
and ends up reading different values at different points in time from an
external store. This can cause strange bugs that are hard to debug at
development time. Selectively using external subscriptions can have huge
performance gains, but have to be used with wisdom and judgement, and according
to the React team should not be the default.

This long train of thought about state management, rendering methods, and
performance is why the (pretty rough, not optimized) hiccup interperter was
removed when moving from the hx name to helix. It didn't fit with the overall
goal of giving users a performant, ergonomic default method for creating React
applications.

If you want to use libraries like [sablono](https://github.com/r0man/sablono),
[hicada](https://github.com/rauhs/hicada) or even [hx](https://github.com/Lokeh/hx/)
hiccup parser, you can easily add that by [creating a custom macro](./pro-tips.md#create-a-custom-macro).
