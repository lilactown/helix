export function createComponent(superclass, spec, statics) {
  let component = class HelixComponent extends superclass {
    constructor(props) {
      super(props);
      if (spec.constructor) {
        spec.constructor.call(this, this);
      }

      for (let key in spec) {
        if (key !== "constructor") {
          let prop = spec[key];
          if (typeof prop === "function") {
            this[key] = prop.bind(this, this);
          } else {
            this[key] = prop;
          }
        }
      }
    }
  };

  for (let key in statics) {
    let prop = statics[key];
    if (typeof prop === "function") {
      component[key] = prop.bind(component, component);
    } else {
      component[key] = prop;
    }
  }

  return component;
}
