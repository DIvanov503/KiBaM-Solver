%% data
%SL750
mA=[20 ,6  ,2  ];
Ah=[0.5,1.0,1.1];
h=Ah./(mA./1000);
%% solution
x=h;
f=Ah./Ah(end);
fun = @(c,x) x./h(end).*((1-exp(-c(2)*h(end)))*(1-c(1))+c(2)*c(1)*h(end))./((1-exp(-c(2)*x))*(1-c(1))+c(2)*c(1)*x);
c0=[0.5,0.5]
cc=lsqcurvefit(fun,c0,x,f,[0,0],[1,inf])
Ifit=fun(cc,x);
plot(x,Ifit)
hold on
plot(x,f(:))
plot(x(1):0.1:x(end),fun(cc,x(1):0.1:x(end)))
hold off
sum((Ifit(:)-f(:)).^2./length(f))
q_max=Ah.*((1-exp(-cc(2)*h)).*(1-cc(1)+cc(2)*cc(1)*h))./(cc(2)*cc(1)*h)