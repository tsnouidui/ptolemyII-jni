within ;
model firstOrder
  Real x(start = 0.0);
  Modelica.Blocks.Interfaces.RealInput u;
equation
  der( x) = u;
  annotation (uses(Modelica(version="3.2.1")));
end firstOrder;
